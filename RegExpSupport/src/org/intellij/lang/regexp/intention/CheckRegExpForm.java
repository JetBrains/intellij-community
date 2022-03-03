// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.lang.regexp.intention;

import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.icons.AllIcons;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.lang.Language;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actions.IncrementalFindAction;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.psi.*;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollBar;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.Alarm;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.intellij.lang.regexp.*;
import org.intellij.lang.regexp.psi.RegExpGroup;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.awt.*;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Konstantin Bulenkov
 */
public final class CheckRegExpForm {
  private static final Logger LOG = Logger.getInstance(CheckRegExpForm.class);

  public static final Key<Boolean> CHECK_REG_EXP_EDITOR = Key.create("CHECK_REG_EXP_EDITOR");

  private static final Key<List<RegExpMatch>> LATEST_MATCHES = Key.create("REG_EXP_LATEST_MATCHES");
  private static final Key<RegExpMatchResult> RESULT = Key.create("REG_EXP_RESULT");

  private static final String LAST_EDITED_REGEXP = "last.edited.regexp";

  private final EditorTextField myRegExp;
  private final EditorTextField mySampleText;

  private final JPanel myRootPanel;
  private final JBLabel myRegExpIcon = new JBLabel();
  private final JBLabel mySampleIcon = new JBLabel();

  private final List<RangeHighlighter> mySampleHighlights = new SmartList<>();
  private RangeHighlighter myRegExpHighlight = null;

  public CheckRegExpForm(@NotNull PsiFile regExpFile) {
    final Project project = regExpFile.getProject();
    final Document document = PsiDocumentManager.getInstance(project).getDocument(regExpFile);

    final Language language = regExpFile.getLanguage();
    final LanguageFileType fileType;
    if (language instanceof RegExpLanguage) {
      fileType = RegExpLanguage.INSTANCE.getAssociatedFileType();
    }
    else {
      // for correct syntax highlighting
      fileType = RegExpFileType.forLanguage(language);
    }
    myRegExp = new EditorTextField(document, project, fileType, false, false) {
      private Disposable disposable;

      @Override
      protected void onEditorAdded(@NotNull Editor editor) {
        super.onEditorAdded(editor);
        disposable = PluginManager.getInstance().createDisposable(CheckRegExpForm.class);
        editor.getCaretModel().addCaretListener(new CaretListener() {

          @Override
          public void caretPositionChanged(@NotNull CaretEvent event) {
            final int offset = editor.logicalPositionToOffset(event.getNewPosition());
            final RegExpGroup group = findCapturingGroupAtOffset(regExpFile, offset);
            final HighlightManager highlightManager = HighlightManager.getInstance(regExpFile.getProject());
            removeHighlights(highlightManager);
            if (group != null) {
              final int index = SyntaxTraverser.psiTraverser(regExpFile).filter(RegExpGroup.class).indexOf(e -> e == group) + 1;
              highlightRegExpGroup(group, highlightManager);
              highlightMatchGroup(highlightManager, getMatches(regExpFile), index);
            }
            else {
              highlightMatchGroup(highlightManager, getMatches(regExpFile), 0);
            }
          }
        }, disposable);
      }

      @Override
      public void removeNotify() {
        super.removeNotify();
        removeHighlights(HighlightManager.getInstance(regExpFile.getProject()));
        Disposer.dispose(disposable);
      }

      @Override
      protected @NotNull EditorEx createEditor() {
        final EditorEx editor = super.createEditor();
        editor.putUserData(CHECK_REG_EXP_EDITOR, Boolean.TRUE);
        editor.putUserData(IncrementalFindAction.SEARCH_DISABLED, Boolean.TRUE);
        editor.setEmbeddedIntoDialogWrapper(true);
        return editor;
      }

      @Override
      public Dimension getPreferredSize() {
        final Dimension size = super.getPreferredSize();
        if (size.height > 250) {
          size.height = 250;
        }
        return size;
      }

      @Override
      protected void updateBorder(@NotNull EditorEx editor) {
        setupBorder(editor);
      }
    };
    setupIcon(myRegExp, myRegExpIcon);

    final String sampleText =
      PropertiesComponent.getInstance(project).getValue(LAST_EDITED_REGEXP, RegExpBundle.message("checker.sample.text"));
    mySampleText = new EditorTextField(sampleText, project, PlainTextFileType.INSTANCE) {
      private Disposable disposable;

      @Override
      protected void onEditorAdded(@NotNull Editor editor) {
        super.onEditorAdded(editor);
        disposable = PluginManager.getInstance().createDisposable(CheckRegExpForm.class);
        editor.getCaretModel().addCaretListener(new CaretListener() {

          @Override
          public void caretPositionChanged(@NotNull CaretEvent event) {
            final int offset = editor.logicalPositionToOffset(event.getNewPosition());
            final HighlightManager highlightManager = HighlightManager.getInstance(regExpFile.getProject());
            removeHighlights(highlightManager);

            final List<RegExpMatch> matches = getMatches(regExpFile);
            int index = indexOfGroupAtOffset(matches, offset);
            if (index > 0) {
              @Nullable RegExpGroup group =
                SyntaxTraverser.psiTraverser(regExpFile)
                  .filter(RegExpGroup.class)
                  .filter(RegExpGroup::isCapturing)
                  .get(index - 1);
              highlightRegExpGroup(group, highlightManager);
              highlightMatchGroup(highlightManager, matches, index);
            }
            else {
              highlightMatchGroup(highlightManager, matches, 0);
            }
          }
        }, disposable);
      }

      @Override
      public void removeNotify() {
        super.removeNotify();
        removeHighlights(HighlightManager.getInstance(regExpFile.getProject()));
        Disposer.dispose(disposable);
      }

      @Override
      protected @NotNull EditorEx createEditor() {
        final EditorEx editor = super.createEditor();
        editor.putUserData(IncrementalFindAction.SEARCH_DISABLED, Boolean.TRUE);
        editor.setEmbeddedIntoDialogWrapper(true);
        return editor;
      }

      @Override
      public Dimension getPreferredSize() {
        final Dimension size = super.getPreferredSize();
        if (size.height > 250) {
          size.height = 250;
        }
        return size;
      }

      @Override
      protected void updateBorder(@NotNull EditorEx editor) {
        setupBorder(editor);
      }
    };

    setupIcon(mySampleText, mySampleIcon);
    mySampleText.setOneLineMode(false);
    final int preferredWidth = Math.max(JBUIScale.scale(250), myRegExp.getPreferredSize().width);
    myRegExp.setPreferredWidth(preferredWidth);
    mySampleText.setPreferredWidth(preferredWidth);

    myRootPanel = new JPanel(new GridBagLayout()) {
      Disposable disposable;
      Alarm updater;

      @Override
      public void addNotify() {
        super.addNotify();
        disposable = Disposer.newDisposable();

        IdeFocusManager.getGlobalInstance().requestFocus(mySampleText, true);

        registerFocusShortcut(myRegExp, "shift TAB", mySampleText);
        registerFocusShortcut(myRegExp, "TAB", mySampleText);
        registerFocusShortcut(mySampleText, "shift TAB", myRegExp);
        registerFocusShortcut(mySampleText, "TAB", myRegExp);

        updater = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, disposable);
        final DocumentListener documentListener = new DocumentListener() {
          @Override
          public void documentChanged(@NotNull DocumentEvent e) {
            update();
          }
        };
        myRegExp.addDocumentListener(documentListener);
        mySampleText.addDocumentListener(documentListener);

        update();
        mySampleText.selectAll();
      }

      private void registerFocusShortcut(JComponent source, String shortcut, EditorTextField target) {
        final AnAction action = new AnAction() {
          @Override
          public void actionPerformed(@NotNull AnActionEvent e) {
            IdeFocusManager.findInstance().requestFocus(target.getFocusTarget(), true);
          }
        };
        action.registerCustomShortcutSet(CustomShortcutSet.fromString(shortcut), source);
      }

      private void update() {
        // triggers resizing of balloon when necessary
        myRootPanel.revalidate();
        Balloon balloon = JBPopupFactory.getInstance().getParentBalloonFor(myRootPanel);
        if (balloon != null && !balloon.isDisposed()) balloon.revalidate();

        updater.cancelAllRequests();
        if (!updater.isDisposed()) {
          updater.addRequest(() -> {
            final RegExpMatchResult result = isMatchingText(regExpFile, myRegExp.getText(), mySampleText.getText());
            regExpFile.putUserData(RESULT, result);
            if (result != RegExpMatchResult.MATCHES && result != RegExpMatchResult.FOUND) {
              setMatches(regExpFile, null);
            }
            ApplicationManager.getApplication().invokeLater(() -> reportResult(result, regExpFile), ModalityState.any(), __ -> updater.isDisposed());
          }, 0);
        }
      }

      @Override
      public void removeNotify() {
        super.removeNotify();
        Disposer.dispose(disposable);
        PropertiesComponent.getInstance(project).setValue(LAST_EDITED_REGEXP, mySampleText.getText());
      }
    };
    myRootPanel.setBorder(JBUI.Borders.empty(UIUtil.DEFAULT_VGAP, UIUtil.DEFAULT_HGAP));

    final GridBagConstraints c = new GridBagConstraints();
    c.insets = JBUI.insets(UIUtil.DEFAULT_VGAP / 2, UIUtil.DEFAULT_HGAP / 2);
    c.gridx = 0;
    c.gridy = 0;
    myRootPanel.add(createLabel(RegExpBundle.message("label.regexp"), myRegExp), c);
    c.gridx = 1;
    myRootPanel.add(myRegExp, c);
    c.gridx = 0;
    c.gridy++;
    myRootPanel.add(createLabel(RegExpBundle.message("label.sample"), mySampleText), c);
    c.gridx = 1;
    myRootPanel.add(mySampleText, c);
  }

  private static int indexOfGroupAtOffset(List<RegExpMatch> matches, int offset) {
    int index = -1;
    for (RegExpMatch match : matches) {
      final int count = match.count();
      for (int i = 0; i < count; i++) {
        final int start = match.start(i);
        if (start <= offset && match.end(i) >= offset) {
          index = i;
          // don't break here, because there may be a better matching group inside the current group
        }
        else if (start > offset) {
          break;
        }
      }
    }
    return index;
  }

  private static RegExpGroup findCapturingGroupAtOffset(@NotNull PsiFile regExpFile, int offset) {
    PsiElement element = regExpFile.findElementAt(offset);
    RegExpGroup group = null;
    while (element != null) {
      if (element instanceof RegExpGroup) {
        final RegExpGroup g = (RegExpGroup)element;
        if (g.isCapturing()) {
          group = g;
          break;
        }
      }
      element = element.getParent();
    }
    return group;
  }

  private void highlightMatchGroup(HighlightManager highlightManager, List<RegExpMatch> matches, int group) {
    final Editor editor = mySampleText.getEditor();
    if (editor == null) {
      return;
    }
    for (RegExpMatch match : matches) {
      final int start = match.start(group);
      final int end = match.end(group);
      if (start < 0 || end < 0) continue;
      if (group != 0 || start != 0 || end != mySampleText.getText().length()) {
        highlightManager.addRangeHighlight(editor, start, end, RegExpHighlighter.MATCHED_GROUPS, true, mySampleHighlights);
      }
    }
  }

  private void highlightRegExpGroup(RegExpGroup group, HighlightManager highlightManager) {
    Editor editor = myRegExp.getEditor();
    if (editor == null) {
      return;
    }
    final PsiElement[] array = {group};
    List<RangeHighlighter> highlighter = new SmartList<>();
    highlightManager.addOccurrenceHighlights(editor, array, RegExpHighlighter.MATCHED_GROUPS, true, highlighter);
    myRegExpHighlight = highlighter.get(0);
  }

  private void removeHighlights(HighlightManager highlightManager) {
    final Editor sampleEditor = mySampleText.getEditor();
    if (sampleEditor != null) {
      for (RangeHighlighter highlighter : mySampleHighlights) {
        highlightManager.removeSegmentHighlighter(sampleEditor, highlighter);
      }
      mySampleHighlights.clear();
    }
    final Editor regExpEditor = myRegExp.getEditor();
    if (myRegExpHighlight != null && regExpEditor != null) {
      highlightManager.removeSegmentHighlighter(regExpEditor, myRegExpHighlight);
      myRegExpHighlight = null;
    }
  }

  private static JLabel createLabel(@NotNull @NlsContexts.Label String labelText, @NotNull JComponent component) {
    final JLabel label = new JLabel(UIUtil.removeMnemonic(labelText));
    final int index = UIUtil.getDisplayMnemonicIndex(labelText);
    if (index != -1) {
      label.setDisplayedMnemonic(labelText.charAt(index + 1));
      label.setDisplayedMnemonicIndex(index);
    }
    label.setLabelFor(component);
    return label;
  }

  private static void setupIcon(@NotNull EditorTextField field, @NotNull JComponent icon) {
    field.addSettingsProvider(editor -> {
      icon.setBorder(JBUI.Borders.emptyLeft(2));
      final JScrollPane scrollPane = editor.getScrollPane();
      scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
      scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
      final JScrollBar verticalScrollBar = scrollPane.getVerticalScrollBar();
      verticalScrollBar.setBackground(editor.getBackgroundColor());
      verticalScrollBar.add(JBScrollBar.LEADING, icon);
      verticalScrollBar.setOpaque(true);
    });
  }

  void reportResult(RegExpMatchResult result, @NotNull PsiFile regExpFile) {
    switch (result) {
      case NO_MATCH:
        setIconAndTooltip(mySampleIcon, AllIcons.General.BalloonError, RegExpBundle.message("tooltip.no.match"));
        setIconAndTooltip(myRegExpIcon, null, null);
        break;
      case MATCHES:
        setIconAndTooltip(mySampleIcon, AllIcons.General.InspectionsOK, RegExpBundle.message("tooltip.matches"));
        setIconAndTooltip(myRegExpIcon, null, null);
        break;
      case FOUND:
        final List<RegExpMatch> matches = getMatches(regExpFile);
        final Editor editor = mySampleText.getEditor();
        if (editor != null) {
          ApplicationManager.getApplication().invokeLater(() -> {
            final HighlightManager highlightManager = HighlightManager.getInstance(regExpFile.getProject());
            removeHighlights(highlightManager);
            highlightMatchGroup(highlightManager, matches, 0);
          });
        }
        if (matches.size() > 1) {
          setIconAndTooltip(mySampleIcon, AllIcons.General.InspectionsOK, RegExpBundle.message("tooltip.found.multiple", matches.size()));
        }
        else {
          setIconAndTooltip(mySampleIcon, AllIcons.General.InspectionsOK, RegExpBundle.message("tooltip.found"));
        }
        setIconAndTooltip(myRegExpIcon, null, null);
        break;
      case INCOMPLETE:
        setIconAndTooltip(mySampleIcon, AllIcons.General.BalloonWarning, RegExpBundle.message("tooltip.more.input.expected"));
        setIconAndTooltip(myRegExpIcon, null, null);
        break;
      case BAD_REGEXP:
        setIconAndTooltip(mySampleIcon, null, null);
        setIconAndTooltip(myRegExpIcon, AllIcons.General.BalloonError, RegExpBundle.message("tooltip.bad.pattern"));
        break;
      case TIMEOUT:
        setIconAndTooltip(mySampleIcon, null, null);
        setIconAndTooltip(myRegExpIcon, AllIcons.General.BalloonWarning, RegExpBundle.message("tooltip.pattern.is.too.complex"));
        break;
    }
  }

  private static void setIconAndTooltip(JBLabel label, Icon icon, @NlsContexts.Tooltip String tooltip) {
    label.setIcon(icon);
    label.setToolTipText(tooltip);
  }

  @NotNull
  public JComponent getPreferredFocusedComponent() {
    return mySampleText;
  }

  @NotNull
  public JPanel getRootPanel() {
    return myRootPanel;
  }

  @ApiStatus.Internal
  public static @NotNull List<RegExpMatch> getMatches(@NotNull PsiFile regExpFile) {
    return ObjectUtils.notNull(regExpFile.getUserData(LATEST_MATCHES), Collections.emptyList());
  }

  public static void setMatches(@NotNull PsiFile regExpFile, @Nullable List<RegExpMatch> matches) {
    regExpFile.putUserData(LATEST_MATCHES, matches);
  }

  @TestOnly
  public static boolean isMatchingTextTest(@NotNull PsiFile regExpFile, @NotNull String sampleText) {
    return getMatchResult(regExpFile, sampleText) == RegExpMatchResult.MATCHES;
  }

  @TestOnly
  public static RegExpMatchResult getMatchResult(@NotNull PsiFile regExpFile, @NotNull String sampleText) {
    return isMatchingText(regExpFile, regExpFile.getText(), sampleText);
  }

  static RegExpMatchResult isMatchingText(@NotNull final PsiFile regExpFile, String regExpText, @NotNull String sampleText) {
    final Language regExpFileLanguage = regExpFile.getLanguage();
    final RegExpMatcherProvider matcherProvider = RegExpMatcherProvider.EP.forLanguage(regExpFileLanguage);
    if (matcherProvider != null) {
      final RegExpMatchResult result = ReadAction.compute(() -> {
        final PsiLanguageInjectionHost host = InjectedLanguageManager.getInstance(regExpFile.getProject()).getInjectionHost(regExpFile);
        if (host != null) {
          return matcherProvider.matches(regExpText, regExpFile, host, sampleText, 1000L);
        }
        return null;
      });
      if (result != null) {
        return result;
      }
    }

    final Integer patternFlags = ReadAction.compute(() -> {
      final PsiLanguageInjectionHost host = InjectedLanguageManager.getInstance(regExpFile.getProject()).getInjectionHost(regExpFile);
      int flags = 0;
      if (host != null) {
        for (RegExpModifierProvider provider : RegExpModifierProvider.EP.allForLanguage(host.getLanguage())) {
          flags = provider.getFlags(host, regExpFile);
          if (flags > 0) break;
        }
      }
      return flags;
    });

    try {
      //noinspection MagicConstant
      final Matcher matcher = Pattern.compile(regExpText, patternFlags).matcher(StringUtil.newBombedCharSequence(sampleText, 1000));
      if (matcher.matches()) {
        setMatches(regExpFile, collectMatches(matcher));
        return RegExpMatchResult.MATCHES;
      }
      final boolean hitEnd = matcher.hitEnd();
      if (matcher.find()) {
        setMatches(regExpFile, collectMatches(matcher));
        return RegExpMatchResult.FOUND;
      }
      else if (hitEnd) {
        return RegExpMatchResult.INCOMPLETE;
      }
      else {
        return RegExpMatchResult.NO_MATCH;
      }
    }
    catch (ProcessCanceledException ignore) {
      return RegExpMatchResult.TIMEOUT;
    }
    catch (Exception e) {
      LOG.warn(e);
    }

    return RegExpMatchResult.BAD_REGEXP;
  }

  private static SmartList<RegExpMatch> collectMatches(Matcher matcher) {
    final SmartList<RegExpMatch> matches = new SmartList<>();
    do {
      final RegExpMatch match = new RegExpMatch();
      final int count = matcher.groupCount();
      for (int i = 0; i <= count; i++) {
        match.add(matcher.start(i), matcher.end(i));
      }
      matches.add(match);
    } while (matcher.find());
    return matches;
  }
}