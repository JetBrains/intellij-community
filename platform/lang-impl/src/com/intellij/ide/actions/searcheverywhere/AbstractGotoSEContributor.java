// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere;

import com.intellij.codeInsight.navigation.NavigationUtil;
import com.intellij.ide.actions.SearchEverywherePsiRenderer;
import com.intellij.ide.util.EditSourceUtil;
import com.intellij.ide.util.gotoByName.ChooseByNameModel;
import com.intellij.ide.util.gotoByName.ChooseByNamePopup;
import com.intellij.ide.util.gotoByName.FilteringGotoByModel;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.InputEvent;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class AbstractGotoSEContributor<F> implements SearchEverywhereContributor<F> {

  protected static final Pattern patternToDetectLinesAndColumns = Pattern.compile("(.+?)" + // name, non-greedy matching
                                                                                "(?::|@|,| |#|#L|\\?l=| on line | at line |:?\\(|:?\\[)" + // separator
                                                                                "(\\d+)?(?:(?:\\D)(\\d+)?)?" + // line + column
                                                                                "[)\\]]?" // possible closing paren/brace
  );
  protected static final Pattern patternToDetectAnonymousClasses = Pattern.compile("([\\.\\w]+)((\\$[\\d]+)*(\\$)?)");
  protected static final Pattern patternToDetectMembers = Pattern.compile("(.+)(#)(.*)");
  protected static final Pattern patternToDetectSignatures = Pattern.compile("(.+#.*)\\(.*\\)");

  //space character in the end of pattern forces full matches search
  private static final String fullMatchSearchSuffix = " ";

  protected final Project myProject;

  protected AbstractGotoSEContributor(Project project) {
    myProject = project;
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

  private static final Logger LOG = Logger.getInstance(AbstractGotoSEContributor.class);

  @Override
  public ContributorSearchResult<Object> search(String pattern, boolean everywhere, SearchEverywhereContributorFilter<F> filter, ProgressIndicator progressIndicator, int elementsLimit) {
    if (!isDumbModeSupported() && DumbService.getInstance(myProject).isDumb()) {
      return ContributorSearchResult.empty();
    }

    String suffix = pattern.endsWith(fullMatchSearchSuffix) ? fullMatchSearchSuffix : "";
    String searchString = filterControlSymbols(pattern) + suffix;
    FilteringGotoByModel<F> model = createModel(myProject);
    model.setFilterItems(filter.getSelectedElements());
    ChooseByNamePopup popup = ChooseByNamePopup.createPopup(myProject, model, (PsiElement)null);
    ContributorSearchResult.Builder<Object> builder = ContributorSearchResult.builder();
    ApplicationManager.getApplication().runReadAction(() -> {
      popup.getProvider().filterElements(popup, searchString, everywhere, progressIndicator,
                                         o -> addFoundElement(o, model, builder, progressIndicator, elementsLimit)
      );
    });
    Disposer.dispose(popup);

    return builder.build();
  }

  protected boolean addFoundElement(Object element, ChooseByNameModel model, ContributorSearchResult.Builder<Object> resultBuilder,
                                    ProgressIndicator progressIndicator, int elementsLimit) {
    if (progressIndicator.isCanceled()) return false;
    if (element == null) {
      LOG.error("Null returned from " + model + " in " + this);
      return true;
    }

    if (resultBuilder.itemsCount() < elementsLimit ) {
      resultBuilder.addItem(element);
      return true;
    } else {
      resultBuilder.setHasMore(true);
      return false;
    }
  }

  //todo param is unnecessary #UX-1
  protected abstract FilteringGotoByModel<F> createModel(Project project);

  public String filterControlSymbols(String pattern) {
    if (StringUtil.containsAnyChar(pattern, ":,;@[( #") || pattern.contains(" line ") || pattern.contains("?l=")) { // quick test if reg exp should be used
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
  public boolean processSelectedItem(Object selected, int modifiers, String searchText) {
    if (selected instanceof PsiElement) {
      if (!((PsiElement)selected).isValid()) {
        LOG.warn("Cannot navigate to invalid PsiElement");
        return true;
      }

      PsiElement psiElement = preparePsi((PsiElement) selected, modifiers, searchText);
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
  public Object getDataForItem(Object element, String dataId) {
    if (CommonDataKeys.PSI_ELEMENT.is(dataId) && element instanceof PsiElement) {
      return element;
    }

    return null;
  }

  @Override
  public boolean isMultiselectSupported() {
    return true;
  }

  @Override
  public ListCellRenderer getElementsRenderer(JList<?> list) {
    return new SearchEverywherePsiRenderer(list) {
      @Override
      public String getElementText(PsiElement element) {
        if (element instanceof NavigationItem) {
          return Optional.ofNullable(((NavigationItem)element).getPresentation())
                         .map(presentation -> presentation.getPresentableText())
                         .orElse(super.getElementText(element));
        }
        return super.getElementText(element);
      }
    };
  }

  protected boolean isDumbModeSupported() {
    return false;
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
}
