// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere;

import com.intellij.codeInsight.navigation.NavigationUtil;
import com.intellij.ide.actions.QualifiedNameProviderUtil;
import com.intellij.ide.actions.SearchEverywherePsiRenderer;
import com.intellij.ide.util.EditSourceUtil;
import com.intellij.ide.util.gotoByName.ChooseByNameInScopeItemProvider;
import com.intellij.ide.util.gotoByName.ChooseByNameItemProvider;
import com.intellij.ide.util.gotoByName.ChooseByNamePopup;
import com.intellij.ide.util.gotoByName.FilteringGotoByModel;
import com.intellij.ide.util.scopeChooser.ScopeChooserCombo;
import com.intellij.ide.util.scopeChooser.ScopeDescriptor;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.MnemonicHelper;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.ui.OffsetIcon;
import com.intellij.ui.TitledSeparator;
import com.intellij.ui.components.JBList;
import com.intellij.ui.popup.list.ListPopupImpl;
import com.intellij.util.ObjectUtils;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.FindSymbolParameters;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class AbstractGotoSEContributor implements SearchEverywhereContributor<Object> {
  private static final Logger LOG = Logger.getInstance(AbstractGotoSEContributor.class);

  protected static final Pattern patternToDetectLinesAndColumns = Pattern.compile(
    "(.+?)" + // name, non-greedy matching
    "(?::|@|,| |#|#L|\\?l=| on line | at line |:?\\(|:?\\[)" + // separator
    "(\\d+)?(?:\\W(\\d+)?)?" + // line + column
    "[)\\]]?" // possible closing paren/brace
  );
  protected static final Pattern patternToDetectAnonymousClasses = Pattern.compile("([.\\w]+)((\\$[\\d]+)*(\\$)?)");
  protected static final Pattern patternToDetectMembers = Pattern.compile("(.+)(#)(.*)");
  protected static final Pattern patternToDetectSignatures = Pattern.compile("(.+#.*)\\(.*\\)");

  protected final Project myProject;
  protected final PsiElement psiContext;
  protected boolean myEverywhere;
  protected ScopeDescriptor myScopeDescriptor;

  protected AbstractGotoSEContributor(@Nullable Project project, @Nullable PsiElement context) {
    myProject = project;
    psiContext = context;
    myScopeDescriptor = new ScopeDescriptor(project == null ? GlobalSearchScope.EMPTY_SCOPE :
                                            GlobalSearchScope.projectScope(project));
  }

  @NotNull
  @Override
  public String getSearchProviderId() {
    return getClass().getSimpleName();
  }

  @Override
  public boolean isShownInSeparateTab() {
    return true;
  }

  @NotNull
  protected List<AnAction> doGetActions(@NotNull String everywhereText,
                                        @Nullable PersistentSearchEverywhereContributorFilter<?> filter,
                                        @NotNull Runnable onChanged) {
    if (myProject == null || filter == null) return Collections.emptyList();
    ArrayList<AnAction> result = new ArrayList<>();
    if (Registry.is("search.everywhere.show.scopes")) {
      result.add(new ScopeChooserAction() {
        final GlobalSearchScope everywhereScope = GlobalSearchScope.everythingScope(myProject);
        final GlobalSearchScope projectScope = GlobalSearchScope.projectScope(myProject);

        @Override
        void onScopeSelected(@NotNull ScopeDescriptor o) {
          myScopeDescriptor = o;
          onChanged.run();
        }

        @NotNull
        @Override
        ScopeDescriptor getSelectedScope() {
          return myScopeDescriptor;
        }

        @Override
        public boolean isEverywhere() {
          return myScopeDescriptor.scopeEquals(everywhereScope);
        }

        @Override
        public void setEverywhere(boolean everywhere) {
          myScopeDescriptor = new ScopeDescriptor(everywhere ? everywhereScope : projectScope);
          onChanged.run();
        }

        @Override
        public boolean canToggleEverywhere() {
          return myScopeDescriptor.scopeEquals(everywhereScope) ||
                 myScopeDescriptor.scopeEquals(projectScope);
        }
      });
    }
    else {
      result.add(new SearchEverywhereUI.CheckBoxAction(everywhereText) {
        @Override
        public boolean isEverywhere() {
          return myEverywhere;
        }

        @Override
        public void setEverywhere(boolean state) {
          myEverywhere = state;
          onChanged.run();
        }
      });
    }
    result.add(new SearchEverywhereUI.FiltersAction(filter, onChanged));
    return result;
  }

  @Override
  public void fetchElements(@NotNull String pattern,
                            @NotNull ProgressIndicator progressIndicator,
                            @NotNull Processor<? super Object> consumer) {
    if (myProject == null) return; //nowhere to search
    if (!isEmptyPatternSupported() && pattern.isEmpty()) return;

    ProgressIndicatorUtils.yieldToPendingWriteActions();
    ProgressIndicatorUtils.runInReadActionWithWriteActionPriority(() -> {
      if (!isDumbAware() && DumbService.isDumb(myProject)) return;

      FilteringGotoByModel<?> model = createModel(myProject);
      if (progressIndicator.isCanceled()) return;

      PsiElement context = psiContext != null && psiContext.isValid() ? psiContext : null;
      ChooseByNamePopup popup = ChooseByNamePopup.createPopup(myProject, model, context);
      try {
        ChooseByNameItemProvider provider = popup.getProvider();
        GlobalSearchScope scope = Registry.is("search.everywhere.show.scopes")
                                  ? (GlobalSearchScope)ObjectUtils.notNull(myScopeDescriptor.getScope())
                                  : null;
        if (scope != null && provider instanceof ChooseByNameInScopeItemProvider) {
          FindSymbolParameters parameters = FindSymbolParameters.wrap(pattern, scope);
          ((ChooseByNameInScopeItemProvider)provider).filterElements(popup, parameters, progressIndicator, element -> {
            if (progressIndicator.isCanceled()) return false;
            if (element == null) {
              LOG.error("Null returned from " + model + " in " + this);
              return true;
            }
            return consumer.process(element);
          });
        }
        else {
          boolean everywhere = scope == null ? myEverywhere : scope.isSearchInLibraries();
          provider.filterElements(popup, pattern, everywhere, progressIndicator, element -> {
            if (progressIndicator.isCanceled()) return false;
            if (element == null) {
              LOG.error("Null returned from " + model + " in " + this);
              return true;
            }
            return consumer.process(element);
          });
        }
      }
      finally {
        Disposer.dispose(popup);
      }
    }, progressIndicator);
  }

  @NotNull
  protected abstract FilteringGotoByModel<?> createModel(@NotNull Project project);

  @NotNull
  @Override
  public String filterControlSymbols(@NotNull String pattern) {
    if (StringUtil.containsAnyChar(pattern, ":,;@[( #") ||
        pattern.contains(" line ") ||
        pattern.contains("?l=")) { // quick test if reg exp should be used
      return applyPatternFilter(pattern, patternToDetectLinesAndColumns);
    }

    return pattern;
  }

  protected static String applyPatternFilter(String str, Pattern regex) {
    Matcher matcher = regex.matcher(str);
    if (matcher.matches()) {
      return matcher.group(1);
    }

    return str;
  }

  @Override
  public boolean showInFindResults() {
    return true;
  }

  @Override
  public boolean processSelectedItem(@NotNull Object selected, int modifiers, @NotNull String searchText) {
    if (selected instanceof PsiElement) {
      if (!((PsiElement)selected).isValid()) {
        LOG.warn("Cannot navigate to invalid PsiElement");
        return true;
      }

      PsiElement psiElement = preparePsi((PsiElement)selected, modifiers, searchText);
      Navigatable extNavigatable = createExtendedNavigatable(psiElement, searchText, modifiers);
      if (extNavigatable != null && extNavigatable.canNavigate()) {
        extNavigatable.navigate(true);
        return true;
      }

      NavigationUtil.activateFileWithPsiElement(psiElement, openInCurrentWindow(modifiers));
    }
    else {
      EditSourceUtil.navigate(((NavigationItem)selected), true, openInCurrentWindow(modifiers));
    }

    return true;
  }

  @Override
  public Object getDataForItem(@NotNull Object element, @NotNull String dataId) {
    if (CommonDataKeys.PSI_ELEMENT.is(dataId)) {
      if (element instanceof PsiElement) {
        return element;
      }
      if (element instanceof DataProvider) {
        return ((DataProvider)element).getData(dataId);
      }
    }

    if (SearchEverywhereDataKeys.ITEM_STRING_DESCRIPTION.is(dataId) && element instanceof PsiElement) {
      return QualifiedNameProviderUtil.getQualifiedName((PsiElement)element);
    }

    return null;
  }

  @Override
  public boolean isMultiSelectionSupported() {
    return true;
  }

  @Override
  public boolean isDumbAware() {
    return DumbService.isDumbAware(createModel(myProject));
  }

  @NotNull
  @Override
  public ListCellRenderer<Object> getElementsRenderer() {
    //noinspection unchecked
    return new SERenderer();
  }

  @Override
  public int getElementPriority(@NotNull Object element, @NotNull String searchPattern) {
    return 50;
  }

  @Nullable
  protected Navigatable createExtendedNavigatable(PsiElement psi, String searchText, int modifiers) {
    VirtualFile file = PsiUtilCore.getVirtualFile(psi);
    Pair<Integer, Integer> position = getLineAndColumn(searchText);
    boolean positionSpecified = position.first >= 0 || position.second >= 0;
    if (file != null && positionSpecified) {
      OpenFileDescriptor descriptor = new OpenFileDescriptor(psi.getProject(), file, position.first, position.second);
      return descriptor.setUseCurrentWindow(openInCurrentWindow(modifiers));
    }

    return null;
  }

  protected PsiElement preparePsi(PsiElement psiElement, int modifiers, String searchText) {
    return psiElement.getNavigationElement();
  }

  protected static Pair<Integer, Integer> getLineAndColumn(String text) {
    int line = getLineAndColumnRegexpGroup(text, 2);
    int column = getLineAndColumnRegexpGroup(text, 3);

    if (line == -1 && column != -1) {
      line = 0;
    }

    return new Pair<>(line, column);
  }

  private static int getLineAndColumnRegexpGroup(String text, int groupNumber) {
    final Matcher matcher = patternToDetectLinesAndColumns.matcher(text);
    if (matcher.matches()) {
      try {
        if (groupNumber <= matcher.groupCount()) {
          final String group = matcher.group(groupNumber);
          if (group != null) return Integer.parseInt(group) - 1;
        }
      }
      catch (NumberFormatException ignored) {
      }
    }

    return -1;
  }

  protected static boolean openInCurrentWindow(int modifiers) {
    return (modifiers & InputEvent.SHIFT_MASK) == 0;
  }

  protected static class SERenderer extends SearchEverywherePsiRenderer {
    @Override
    public String getElementText(PsiElement element) {
      if (element instanceof NavigationItem) {
        return Optional.ofNullable(((NavigationItem)element).getPresentation())
          .map(presentation -> presentation.getPresentableText())
          .orElse(super.getElementText(element));
      }
      return super.getElementText(element);
    }
  }

  abstract static class ScopeChooserAction extends ActionGroup
    implements CustomComponentAction, DumbAware, SearchEverywhereUI.EverywhereToggleAction {

    static char MNEMONIC = 'P';

    abstract void onScopeSelected(@NotNull ScopeDescriptor o);

    @NotNull
    abstract ScopeDescriptor getSelectedScope();

    @Override public boolean canBePerformed(@NotNull DataContext context) { return true; }
    @Override public boolean isPopup() { return true; }
    @NotNull @Override public AnAction[] getChildren(@Nullable AnActionEvent e) { return EMPTY_ARRAY; }

    @NotNull @Override
    public JComponent createCustomComponent(@NotNull Presentation presentation, @NotNull String place) {
      JComponent c = new ActionButtonWithText(this, presentation, place, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE) {
        @Override
        public int getMnemonic() {
          return KeyEvent.getExtendedKeyCodeForChar(MNEMONIC);
        }
      };
      MnemonicHelper.registerMnemonicAction(c, MNEMONIC);
      return c;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      ScopeDescriptor selection = getSelectedScope();
      String name = StringUtil.trimMiddle(selection.getDisplayName(), 30);
      String text = StringUtil.escapeMnemonics(name)
        .replace(String.valueOf(Character.toLowerCase(MNEMONIC)), "_" + Character.toLowerCase(MNEMONIC))
        .replace(String.valueOf(Character.toUpperCase(MNEMONIC)), "_" + Character.toUpperCase(MNEMONIC));
      e.getPresentation().setText(text);
      e.getPresentation().setIcon(OffsetIcon.getOriginalIcon(selection.getIcon()));
      String shortcutText = KeymapUtil.getKeystrokeText(KeyStroke.getKeyStroke(
        MNEMONIC, MnemonicHelper.getFocusAcceleratorKeyMask(), true));
      e.getPresentation().setDescription("Choose scope (" + shortcutText +")");
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      JComponent button = e.getPresentation().getClientProperty(CustomComponentAction.COMPONENT_KEY);
      if (button == null || !button.isValid()) return;
      JList<ScopeDescriptor> fakeList = new JBList<>();
      ListCellRenderer<ScopeDescriptor> renderer = new ListCellRenderer<ScopeDescriptor>() {
        final ListCellRenderer<ScopeDescriptor> delegate = ScopeChooserCombo.createDefaultRenderer();
        @Override
        public Component getListCellRendererComponent(JList<? extends ScopeDescriptor> list,
                                                      ScopeDescriptor value,
                                                      int index,
                                                      boolean isSelected,
                                                      boolean cellHasFocus) {
          // copied from DarculaJBPopupComboPopup.customizeListRendererComponent()
          Component component = delegate.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
          if (component instanceof JComponent &&
              !(component instanceof JSeparator || component instanceof TitledSeparator)) {
            ((JComponent)component).setBorder(JBUI.Borders.empty(2, 8));
          }
          return component;
        }
      };
      List<ScopeDescriptor> items = new ArrayList<>();
      ScopeChooserCombo.processScopes(e.getRequiredData(CommonDataKeys.PROJECT),
                                      e.getDataContext(),
                                      ScopeChooserCombo.OPT_LIBRARIES | ScopeChooserCombo.OPT_EMPTY_SCOPES, o -> {
          Component c = renderer.getListCellRendererComponent(fakeList, o, -1, false, false);
          if (c instanceof JSeparator || c instanceof TitledSeparator ||
              !o.scopeEquals(null) && o.getScope() instanceof GlobalSearchScope) {
            items.add(o);
          }
          return true;
        });
      BaseListPopupStep<ScopeDescriptor> step = new BaseListPopupStep<ScopeDescriptor>("", items) {
        @Nullable
        @Override
        public PopupStep onChosen(ScopeDescriptor selectedValue, boolean finalChoice) {
          onScopeSelected(selectedValue);
          ActionToolbar toolbar = UIUtil.uiParents(button, true).filter(ActionToolbar.class).first();
          if (toolbar != null) toolbar.updateActionsImmediately();
          return FINAL_CHOICE;
        }

        @Override
        public boolean isSpeedSearchEnabled() {
          return true;
        }

        @NotNull
        @Override
        public String getTextFor(ScopeDescriptor value) {
          return value.getScope() instanceof GlobalSearchScope ? value.getDisplayName() : "";
        }

        @Override
        public boolean isSelectable(ScopeDescriptor value) {
          return value.getScope() instanceof GlobalSearchScope;
        }
      };
      ScopeDescriptor selection = getSelectedScope();
      step.setDefaultOptionIndex(ContainerUtil.indexOf(items, o ->
        Comparing.equal(o.getDisplayName(), selection.getDisplayName())));
      ListPopupImpl popup = new ListPopupImpl(e.getProject(), step);
      popup.setMaxRowCount(10);
      //noinspection unchecked
      popup.getList().setCellRenderer(renderer);
      popup.showUnderneathOf(button);
    }
  }
}
