/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide.util.gotoByName;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.ide.actions.ChooseByNameItemProvider;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.actionSystem.ShortcutSet;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.UnknownFileType;
import com.intellij.openapi.fileTypes.ex.FileTypeManagerEx;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.openapi.progress.util.ReadTask;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.codeStyle.MinusculeMatcher;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.psi.statistics.StatisticsInfo;
import com.intellij.psi.statistics.StatisticsManager;
import com.intellij.ui.CollectionListModel;
import com.intellij.util.Alarm;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.text.Matcher;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.regex.Pattern;

public abstract class ChooseByNameViewModel {
  public static final String EXTRA_ELEM = "...";
  public static final String NON_PREFIX_SEPARATOR = "non-prefix matches:";
  public static final Pattern patternToDetectLinesAndColumns = Pattern.compile("(.+?)" +
                                                                                // name, non-greedy matching
                                                                                "(?::|@|,| |#L| on line | at line |:?\\(|:?\\[)" +
                                                                                // separator
                                                                                "(\\d+)(?:(?:\\D)(\\d+)?)?" +
                                                                                // line + column
                                                                                "[)\\]]?" // possible closing paren/brace
  );
  public static final Pattern patternToDetectAnonymousClasses = Pattern.compile("([\\.\\w]+)((\\$[\\d]+)*(\\$)?)");
  public static final Pattern patternToDetectMembers = Pattern.compile("(.+)(#)(.*)");
  protected static final String ACTION_NAME = "Show All in View";
  @NonNls protected static final String NOT_FOUND_IN_PROJECT_CARD = "syslib";
  @NonNls protected static final String NOT_FOUND_CARD = "nfound";
  @NonNls protected static final String CHECK_BOX_CARD = "chkbox";
  @NonNls protected static final String SEARCHING_CARD = "searching";
  static final boolean ourLoadNamesEachTime = FileBasedIndex.ourEnableTracingOfKeyHashToVirtualFileMapping;
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.util.gotoByName.ChooseByNameBase");
  protected static int VISIBLE_LIST_SIZE_LIMIT = 10;
  protected final Project myProject;
  protected final ChooseByNameModel myModel;
  protected final String myInitialText;
  protected final List<Pair<String, Integer>> myHistory = ContainerUtil.newArrayList();
  protected final List<Pair<String, Integer>> myFuture = ContainerUtil.newArrayList();
  protected final Alarm myAlarm = new Alarm();
  protected final int myRebuildDelay;
  protected final Alarm myHideAlarm = new Alarm();
  protected final int myInitialIndex;
  protected final ListUpdater myListUpdater = new ListUpdater();
  protected final MyListModel myListModel = new MyListModel();
  private final String[][] myNames = new String[2][];
  protected ChooseByNameItemProvider myProvider;
  protected boolean mySearchInAnyPlace = false;
  protected boolean myInitialized;
  protected ChooseByNamePopupComponent.Callback myActionListener;
  protected volatile CalcElementsThread myCalcElementsThread;
  protected int myListSizeIncreasing = 30;
  protected int myMaximumListSizeLimit = 30;
  protected boolean myShowListAfterCompletionKeyStroke = false;
  protected boolean myClosedByShiftEnter = false;
  protected String myFindUsagesTitle;
  protected boolean myInitIsDone;
  private boolean myDisposedFlag = false;
  private boolean myAlwaysHasMore = false;

  public ChooseByNameViewModel(Project project,
                               @NotNull ChooseByNameModel model,
                               @NotNull ChooseByNameItemProvider provider,
                               String initialText,
                               final int initialIndex) {
    myProject = project;
    myProvider = provider;
    myInitialText = initialText;
    myInitialIndex = initialIndex;
    mySearchInAnyPlace = Registry.is("ide.goto.middle.matching") && model.useMiddleMatching();
    myModel = model;
    myRebuildDelay = Registry.intValue("ide.goto.rebuild.delay");
    myInitIsDone = true;
  }

  @NotNull
  protected static Set<KeyStroke> getShortcuts(@NotNull String actionId) {
    Set<KeyStroke> result = new HashSet<KeyStroke>();
    for (Shortcut shortcut : KeymapManager.getInstance().getActiveKeymap().getShortcuts(actionId)) {
      if (shortcut instanceof KeyboardShortcut) {
        KeyboardShortcut keyboardShortcut = (KeyboardShortcut)shortcut;
        result.add(keyboardShortcut.getFirstKeyStroke());
      }
    }
    return result;
  }

  protected static boolean isFileName(String name) {
    final int index = name.lastIndexOf('.');
    if (index > 0) {
      String ext = name.substring(index + 1);
      if (ext.contains(":")) {
        ext = ext.substring(0, ext.indexOf(':'));
      }
      if (FileTypeManagerEx.getInstanceEx().getFileTypeByExtension(ext) != UnknownFileType.INSTANCE) {
        return true;
      }
    }
    return false;
  }

  protected static Matcher buildPatternMatcher(@NotNull String pattern) {
    return NameUtil.buildMatcher(pattern, NameUtil.MatchingCaseSensitivity.NONE);
  }

  public boolean checkDisposed() {
    return myDisposedFlag;
  }

  public void setDisposed(boolean disposedFlag) {
    myDisposedFlag = disposedFlag;
    if (disposedFlag) {
      setNamesSync(true, null);
      setNamesSync(false, null);
    }
  }

  private void setNamesSync(boolean checkboxState, @Nullable String[] value) {
    synchronized (myNames) {
      myNames[checkboxState ? 1 : 0] = value;
    }
  }

  public boolean isSearchInAnyPlace() {
    return mySearchInAnyPlace;
  }

  public void setSearchInAnyPlace(boolean searchInAnyPlace) {
    mySearchInAnyPlace = searchInAnyPlace;
  }

  public boolean isClosedByShiftEnter() {
    return myClosedByShiftEnter;
  }

  public boolean isOpenInCurrentWindowRequested() {
    return isClosedByShiftEnter();
  }

  public abstract void setToolArea(JComponent toolArea);

  public void setFindUsagesTitle(@Nullable String findUsagesTitle) {
    myFindUsagesTitle = findUsagesTitle;
  }

  public abstract void invoke(ChooseByNamePopupComponent.Callback callback, ModalityState modalityState, boolean allowMultipleSelection);

  @NotNull
  public ChooseByNameModel getModel() {
    return myModel;
  }

  @Nullable
  public String getMemberPattern() {
    final String enteredText = getTrimmedText();
    final int index = enteredText.lastIndexOf('#');
    if (index == -1) {
      return null;
    }

    String name = enteredText.substring(index + 1).trim();
    return StringUtil.isEmpty(name) ? null : name;
  }

  public int getLinePosition() {
    return getLineOrColumn(true);
  }

  protected int getLineOrColumn(final boolean line) {
    final java.util.regex.Matcher matcher = patternToDetectLinesAndColumns.matcher(getTrimmedText());
    if (matcher.matches()) {
      final int groupNumber = line ? 2 : 3;
      try {
        if (groupNumber <= matcher.groupCount()) {
          final String group = matcher.group(groupNumber);
          if (group != null) return Integer.parseInt(group) - 1;
        }
        if (!line && getLineOrColumn(true) != -1) return 0;
      }
      catch (NumberFormatException ignored) {
      }
    }

    return -1;
  }

  public int getColumnPosition() {
    return getLineOrColumn(false);
  }

  @Nullable
  public String getPathToAnonymous() {
    final java.util.regex.Matcher matcher = patternToDetectAnonymousClasses.matcher(getTrimmedText());
    if (matcher.matches()) {
      String path = matcher.group(2);
      if (path != null) {
        path = path.trim();
        if (path.endsWith("$") && path.length() >= 2) {
          path = path.substring(0, path.length() - 2);
        }
        if (!path.isEmpty()) return path;
      }
    }

    return null;
  }

  public String getAdText() {return "";}

  public void setAdText(String adText) {}

  public abstract void repaintList();

  public abstract String getEnteredText();

  public abstract int getSelectedIndex();

  protected abstract void showCardImpl(String card);

  public abstract void setCheckBoxShortcut(ShortcutSet shortcutSet);

  protected abstract void hideHint();

  protected abstract void doHideHint();

  /**
   * Default rebuild list. It uses {@link #myRebuildDelay} and current modality state.
   */
  public void rebuildList(boolean initial) {
    // TODO this method is public, because the chooser does not listed for the model.
    rebuildList(initial ? myInitialIndex : 0, myRebuildDelay, ModalityState.current(), null);
  }

  protected abstract void updateDocumentation();

  public String transformPattern(String pattern) {
    return pattern;
  }

  protected void doClose(final boolean ok) {
    if (checkDisposed()) return;

    if (closeForbidden(ok)) return;

    cancelListUpdater();
    close(ok);

    myListModel.removeAll();
  }

  protected boolean closeForbidden(boolean ok) {
    return false;
  }

  protected void cancelListUpdater() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (checkDisposed()) return;

    final CalcElementsThread calcElementsThread = myCalcElementsThread;
    if (calcElementsThread != null) {
      calcElementsThread.cancel();
      backgroundCalculationFinished(Collections.emptyList(), 0);
    }
    myListUpdater.cancelAll();
  }

  @NotNull public String getTrimmedText() {
    return StringUtil.trimLeading(StringUtil.notNullize(getEnteredText()));
  }

  @NotNull
  protected synchronized String[] ensureNamesLoaded(boolean checkboxState) {
    String[] cached = getNamesSync(checkboxState);
    if (cached != null) return cached;

    if (checkboxState &&
        myModel instanceof ContributorsBasedGotoByModel &&
        ((ContributorsBasedGotoByModel)myModel).sameNamesForProjectAndLibraries() &&
        getNamesSync(false) != null) {
      // there is no way in indices to have different keys for project symbols vs libraries, we always have same ones
      String[] allNames = getNamesSync(false);
      setNamesSync(true, allNames);
      return allNames;
    }

    String[] result = myModel.getNames(checkboxState);
    //noinspection ConstantConditions
    assert result != null : "Model "+myModel+ "("+myModel.getClass()+") returned null names";
    setNamesSync(checkboxState, result);

    return result;
  }

  @NotNull
  public String[] getNames(boolean checkboxState) {
    if (ourLoadNamesEachTime) {
      setNamesSync(checkboxState, null);
      return ensureNamesLoaded(checkboxState);
    }
    return getNamesSync(checkboxState);
  }

  private String[] getNamesSync(boolean checkboxState) {
    synchronized (myNames) {
      return myNames[checkboxState ? 1 : 0];
    }
  }

  @NotNull
  protected Set<Object> filter(@NotNull Set<Object> elements) {
    return elements;
  }

  protected abstract boolean isCheckboxVisible();

  protected abstract boolean isShowListForEmptyPattern();

  protected abstract boolean isCloseByFocusLost();

  protected JLayeredPane getLayeredPane() {
    JLayeredPane layeredPane;
    final Window window = WindowManager.getInstance().suggestParentWindow(myProject);

    Component parent = UIUtil.findUltimateParent(window);

    if (parent instanceof JFrame) {
      layeredPane = ((JFrame)parent).getLayeredPane();
    }
    else if (parent instanceof JDialog) {
      layeredPane = ((JDialog)parent).getLayeredPane();
    }
    else {
      throw new IllegalStateException("cannot find parent window: project=" + myProject +
                                      (myProject != null ? "; open=" + myProject.isOpen() : "") +
                                      "; window=" + window);
    }
    return layeredPane;
  }

  protected void rebuildList(final int pos,
                             final int delay,
                             @NotNull final ModalityState modalityState,
                             @Nullable final Runnable postRunnable) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (!myInitialized) {
      return;
    }

    myAlarm.cancelAllRequests();

    if (delay > 0) {
      myAlarm.addRequest(() -> rebuildList(pos, 0, modalityState, postRunnable), delay, getModalityStateForTextBox());
      return;
    }

    myListUpdater.cancelAll();

    final CalcElementsThread calcElementsThread = myCalcElementsThread;
    if (calcElementsThread != null) {
      calcElementsThread.cancel();
    }

    final String text = getTrimmedText();
    if (!canShowListForEmptyPattern() && text.isEmpty()) {
      myListModel.removeAll();
      hideList();
      doHideHint();
      showCardImpl(CHECK_BOX_CARD);
      return;
    }

    configureListRenderer();

    scheduleCalcElements(text, isCheckboxSelected(), modalityState, elements -> {
      ApplicationManager.getApplication().assertIsDispatchThread();
      backgroundCalculationFinished(elements, pos);

      if (postRunnable != null) {
        postRunnable.run();
      }
    });
  }

  @NotNull
  protected abstract ModalityState getModalityStateForTextBox();

  protected abstract boolean isCheckboxSelected();

  protected abstract void configureListRenderer();

  protected void backgroundCalculationFinished(Collection<?> result, int toSelect) {
    myCalcElementsThread = null;
    setElementsToList(toSelect, result);
    repaintList();
    chosenElementMightChange();

    if (result.isEmpty()) {
      doHideHint();
    }
  }

  public void scheduleCalcElements(String text,
                                   boolean checkboxState,
                                   ModalityState modalityState,
                                   Consumer<Set<?>> callback) {
    new CalcElementsThread(text, checkboxState, callback, modalityState).scheduleThread();
  }

  private boolean isShowListAfterCompletionKeyStroke() {
    return myShowListAfterCompletionKeyStroke;
  }

  protected void setElementsToList(int pos, @NotNull Collection<?> elements) {
    myListUpdater.cancelAll();
    if (checkDisposed()) return;
    if (elements.isEmpty()) {
      myListModel.removeAll();
      setHasResults(false);
      myListUpdater.cancelAll();
      hideList();
      return;
    }

    Object[] oldElements = myListModel.getItems().toArray();
    Object[] newElements = elements.toArray();
    List<ModelDiff.Cmd> commands = ModelDiff.createDiffCmds(myListModel, oldElements, newElements);
    if (commands == null) {
      return; // Nothing changed
    }

    setHasResults(true);
    if (commands.isEmpty()) {
      selectItem(pos, newElements);
      updateVisibleRowCount();
      showList();
      repositionHint();
    }
    else {
      showList();
      myListUpdater.appendToModel(commands, pos);
    }
  }

  protected abstract void setHasResults(boolean b);

  @VisibleForTesting
  public int calcSelectedIndex(Object[] modelElements, String trimmedText) {
    if (myModel instanceof Comparator) {
      return 0;
    }

    Matcher matcher = buildPatternMatcher(transformPattern(trimmedText));

    final String statContext = statisticsContext();
    Comparator<Object> itemComparator = Comparator.
      comparing(e -> trimmedText.equalsIgnoreCase(myModel.getElementName(e))).
      thenComparing(e -> matchingDegree(matcher, e)).
      thenComparing(e -> getUseCount(statContext, e)).
      reversed();

    int bestPosition = 0;
    for (int i = 1; i < modelElements.length; i++) {
      final Object modelElement = modelElements[i];
      if (EXTRA_ELEM.equals(modelElement) || NON_PREFIX_SEPARATOR.equals(modelElement)) continue;

      if (itemComparator.compare(modelElement, modelElements[bestPosition]) < 0) {
          bestPosition = i;
      }
    }

    if (bestPosition < modelElements.length - 1 && modelElements[bestPosition] == NON_PREFIX_SEPARATOR) {
      bestPosition++;
    }

    return bestPosition;
  }

  private int getUseCount(String statContext, Object modelElement) {
    String text = myModel.getFullName(modelElement);
    return text == null ? Integer.MIN_VALUE : StatisticsManager.getInstance().getUseCount(new StatisticsInfo(statContext, text));
  }

  private int matchingDegree(Matcher matcher, Object modelElement) {
    String name = myModel.getElementName(modelElement);
    return name != null && matcher instanceof MinusculeMatcher ? ((MinusculeMatcher)matcher).matchingDegree(name) : Integer.MIN_VALUE;
  }

  protected abstract void showList();

  protected abstract void hideList();

  protected void close(boolean isOk) {
    if (checkDisposed()) {
      return;
    }

    if (isOk) {
      myModel.saveInitialCheckBoxState(isCheckboxSelected());

      final List<Object> chosenElements = getChosenElements();
      if (chosenElements != null) {
        if (myActionListener instanceof ChooseByNamePopupComponent.MultiElementsCallback) {
          ((ChooseByNamePopupComponent.MultiElementsCallback)myActionListener).elementsChosen(chosenElements);
        }
        else {
          for (Object element : chosenElements) {
            myActionListener.elementChosen(element);
            String text = myModel.getFullName(element);
            if (text != null) {
              StatisticsManager.getInstance().incUseCount(new StatisticsInfo(statisticsContext(), text));
            }
          }
        }
      }
      else {
        return;
      }

      if (!chosenElements.isEmpty()) {
        final String enteredText = getTrimmedText();
        if (enteredText.indexOf('*') >= 0) {
          FeatureUsageTracker.getInstance().triggerFeatureUsed("navigation.popup.wildcards");
        }
        else {
          for (Object element : chosenElements) {
            final String name = myModel.getElementName(element);
            if (name != null) {
              if (!StringUtil.startsWithIgnoreCase(name, enteredText)) {
                FeatureUsageTracker.getInstance().triggerFeatureUsed("navigation.popup.camelprefix");
                break;
              }
            }
          }
        }
      }
      else {
        return;
      }
    }

    setDisposed(true);
    myAlarm.cancelAllRequests();

    if (ApplicationManager.getApplication().isUnitTestMode()) return;
    if (myActionListener != null) {
      myActionListener.onClose();
    }
  }

  @NotNull
  @NonNls
  protected String statisticsContext() {
    return "choose_by_name#" + myModel.getPromptText() + "#" + isCheckboxSelected() + "#" + getTrimmedText();
  }

  protected abstract void selectItem(int selectionPos, Object[] newElements);

  protected abstract void repositionHint();

  protected abstract void updateVisibleRowCount();

  @Nullable
  public abstract Object getChosenElement();

  protected abstract List<Object> getChosenElements();

  protected void chosenElementMightChange() {
  }

  public ChooseByNameItemProvider getProvider() {
    return myProvider;
  }

  protected abstract void handlePaste(String str);

  public boolean canShowListForEmptyPattern() {
    return isShowListForEmptyPattern() || isShowListAfterCompletionKeyStroke() && lastKeyStrokeIsCompletion();
  }

  protected abstract boolean lastKeyStrokeIsCompletion();

  public int getMaximumListSizeLimit() {
    return myMaximumListSizeLimit;
  }

  public void setMaximumListSizeLimit(final int maximumListSizeLimit) {
    myMaximumListSizeLimit = maximumListSizeLimit;
  }

  public void setListSizeIncreasing(final int listSizeIncreasing) {
    myListSizeIncreasing = listSizeIncreasing;
  }

  /**
   * Display <tt>...</tt> item at the end of the list regardless of whether it was filled up or not.
   * This option can be useful in cases, when it can't be said beforehand, that the next call to {@link ChooseByNameItemProvider}
   * won't give new items.
   */
  public void setAlwaysHasMore(boolean enabled) {
    myAlwaysHasMore = enabled;
  }

  protected void doShowCard(final CalcElementsThread t, final String card, int delay) {
    if (ApplicationManager.getApplication().isUnitTestMode()) return;
    t.myShowCardAlarm.cancelAllRequests();
    t.myShowCardAlarm.addRequest(() -> {
      if (!t.myProgress.isCanceled()) {
        showCardImpl(card);
      }
    }, delay, t.myModalityState);
  }

  public abstract JTextField getTextField();

  protected static class MyListModel<T> extends CollectionListModel<T> implements ModelDiff.Model<T> {
    @Override
    public void addToModel(int idx, T element) {
        add(Math.min(idx, getSize()), element);
    }

    @Override
    public void addAllToModel(int index, List<T> elements) {
      addAll(Math.min(index, getSize()), elements);
    }

    @Override
    public void removeRangeFromModel(int start, int end) {
      if (start < getSize() && getSize() != 0) {
        removeRange(start, Math.min(end, getSize()-1));
      }
    }
  }

  protected class CalcElementsThread extends ReadTask {
    private final String myPattern;
    private final boolean myCheckboxState;
    private final Consumer<Set<?>> myCallback;
    protected final ModalityState myModalityState;

    protected final ProgressIndicator myProgress = new ProgressIndicatorBase();

    CalcElementsThread(String pattern,
                       boolean checkboxState,
                       Consumer<Set<?>> callback,
                       @NotNull ModalityState modalityState) {
      myPattern = pattern;
      myCheckboxState = checkboxState;
      myCallback = callback;
      myModalityState = modalityState;
    }

    protected final Alarm myShowCardAlarm = new Alarm();

    void scheduleThread() {
      ApplicationManager.getApplication().assertIsDispatchThread();
      myCalcElementsThread = this;
      showCard(SEARCHING_CARD, 200);
      ProgressIndicatorUtils.scheduleWithWriteActionPriority(myProgress, this);
    }

    public Continuation runBackgroundProcess(@NotNull final ProgressIndicator indicator) {
      if (DumbService.isDumbAware(myModel)) return super.runBackgroundProcess(indicator);

      return DumbService.getInstance(myProject).runReadActionInSmartMode(() -> performInReadAction(indicator));
    }


    @Nullable
    @Override
    public Continuation performInReadAction(@NotNull ProgressIndicator indicator) throws ProcessCanceledException {
      if (myProject != null && myProject.isDisposed()) return null;

      final Set<Object> elements = new LinkedHashSet<Object>();
      boolean scopeExpanded = populateElements(elements);
      final String cardToShow = elements.isEmpty() ? NOT_FOUND_CARD : scopeExpanded ? NOT_FOUND_IN_PROJECT_CARD : CHECK_BOX_CARD;


      final Set<Object> filtered = filter(elements);
      return new Continuation(() -> {
        if (!checkDisposed() && !myProgress.isCanceled()) {
          CalcElementsThread currentBgProcess = myCalcElementsThread;
          LOG.assertTrue(currentBgProcess == CalcElementsThread.this, currentBgProcess);

          showCard(cardToShow, 0);

          myCallback.consume(filtered);
        }
      }, myModalityState);
    }

    private boolean populateElements(Set<Object> elements) {
      boolean scopeExpanded = false;
      try {
        scopeExpanded = fillWithScopeExpansion(elements, myPattern);

        String lowerCased = patternToLowerCase(myPattern);
        if (elements.isEmpty() && !lowerCased.equals(myPattern)) {
          scopeExpanded = fillWithScopeExpansion(elements, lowerCased);
        }
      }
      catch (ProcessCanceledException e) {
        throw e;
      }
      catch (Exception e) {
        LOG.error(e);
      }
      return scopeExpanded;
    }

    private boolean fillWithScopeExpansion(Set<Object> elements, String pattern) {
      if (!ourLoadNamesEachTime) ensureNamesLoaded(myCheckboxState);
      addElementsByPattern(pattern, elements, myProgress, myCheckboxState);

      if (elements.isEmpty() && !myCheckboxState) {
        if (!ourLoadNamesEachTime) ensureNamesLoaded(true);
        addElementsByPattern(pattern, elements, myProgress, true);
        return true;
      }
      return false;
    }

    @Override
    public void onCanceled(@NotNull ProgressIndicator indicator) {
      LOG.assertTrue(myCalcElementsThread == this, myCalcElementsThread);
      new ChooseByNameBase.CalcElementsThread(myPattern, myCheckboxState, myCallback, myModalityState).scheduleThread();
    }

    protected void addElementsByPattern(@NotNull String pattern,
                                        @NotNull final Set<Object> elements,
                                        @NotNull final ProgressIndicator indicator,
                                        boolean everywhere) {
      long start = System.currentTimeMillis();
      myProvider.filterElements(
        ChooseByNameViewModel.this, pattern, everywhere,
        indicator, o -> {
          if (indicator.isCanceled()) return false;
          elements.add(o);

          if (isOverflow(elements)) {
            elements.add(EXTRA_ELEM);
            return false;
          }
          return true;
        });
      if (myAlwaysHasMore) {
        elements.add(EXTRA_ELEM);
      }
      if (ContributorsBasedGotoByModel.LOG.isDebugEnabled()) {
        long end = System.currentTimeMillis();
        ContributorsBasedGotoByModel.LOG.debug("addElementsByPattern("+pattern+"): "+(end-start)+"ms; "+elements.size()+" elements");
      }
    }

    protected void showCard(String card, int delay) {
      doShowCard(this, card, delay);
    };

    protected boolean isOverflow(@NotNull Set<Object> elementsArray) {
      return elementsArray.size() >= myMaximumListSizeLimit;
    }

    protected void cancel() {
      ApplicationManager.getApplication().assertIsDispatchThread();
      myProgress.cancel();
    }

  }

  protected class ListUpdater {
    private final Alarm myAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);
    private static final int DELAY = 10;
    private static final int MAX_BLOCKING_TIME = 30;
    private final List<ModelDiff.Cmd> myCommands = Collections.synchronizedList(new ArrayList<ModelDiff.Cmd>());

    public void cancelAll() {
      myCommands.clear();
      myAlarm.cancelAllRequests();
    }

    public void appendToModel(@NotNull List<ModelDiff.Cmd> commands, final int selectionPos) {
      myAlarm.cancelAllRequests();
      myCommands.addAll(commands);

      if (myCommands.isEmpty() || checkDisposed()) {
        return;
      }
      myAlarm.addRequest(new Runnable() {
        @Override
        public void run() {
          if (checkDisposed()) {
            return;
          }
          final long startTime = System.currentTimeMillis();
          while (!myCommands.isEmpty() && System.currentTimeMillis() - startTime < MAX_BLOCKING_TIME) {
            final ModelDiff.Cmd cmd = myCommands.remove(0);
            cmd.apply();
          }

          updateVisibleRowCount();

          if (!myCommands.isEmpty()) {
            myAlarm.addRequest(this, DELAY);
          }
          if (!checkDisposed()) {
            showList();
            repositionHint();

            selectItem(selectionPos, myListModel.getItems().toArray());
          }
        }
      }, DELAY);
    }
  }

  @NotNull
  protected static String patternToLowerCase(String pattern) {
    return pattern.toLowerCase(Locale.US);
  }

}
