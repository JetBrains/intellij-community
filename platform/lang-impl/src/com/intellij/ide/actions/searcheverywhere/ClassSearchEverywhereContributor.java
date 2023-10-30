// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere;

import com.intellij.codeInsight.navigation.NavigationUtil;
import com.intellij.ide.actions.CopyReferenceAction;
import com.intellij.ide.actions.GotoClassPresentationUpdater;
import com.intellij.ide.structureView.StructureView;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.util.gotoByName.FilteringGotoByModel;
import com.intellij.ide.util.gotoByName.GotoClassModel2;
import com.intellij.ide.util.gotoByName.GotoClassSymbolConfiguration;
import com.intellij.ide.util.gotoByName.LanguageRef;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.lang.LanguageStructureViewBuilder;
import com.intellij.lang.PsiStructureViewFactory;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.codeStyle.MinusculeMatcher;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.intellij.ide.actions.searcheverywhere.footer.ExtendedInfoImplKt.createPsiExtendedInfo;

/**
 * @author Konstantin Bulenkov
 */
public class ClassSearchEverywhereContributor extends AbstractGotoSEContributor implements EssentialContributor,
                                                                                           SearchEverywherePreviewProvider {
  private static final Pattern ourPatternToDetectMembers = Pattern.compile("(.+)(#)(.*)");

  private final PersistentSearchEverywhereContributorFilter<LanguageRef> filter;

  public ClassSearchEverywhereContributor(@NotNull AnActionEvent event) {
    super(event);

    filter = createLanguageFilter(event.getRequiredData(CommonDataKeys.PROJECT));
  }

  @Override
  public @NotNull @Nls String getGroupName() {
    return GotoClassPresentationUpdater.getTabTitlePluralized();
  }

  @Override
  public @NotNull String getFullGroupName() {
    //noinspection HardCodedStringLiteral
    @Nls String res = String.join("/", GotoClassPresentationUpdater.getActionTitlePluralized());
    return res;
  }

  @Override
  public int getSortWeight() {
    return 100;
  }

  @Override
  protected @NotNull FilteringGotoByModel<LanguageRef> createModel(@NotNull Project project) {
    GotoClassModel2 model = new GotoClassModel2(project);
    if (filter != null) {
      model.setFilterItems(filter.getSelectedElements());
    }
    return model;
  }

  @Override
  public @NotNull List<AnAction> getActions(@NotNull Runnable onChanged) {
    return doGetActions(filter, new SearchEverywhereFiltersStatisticsCollector.LangFilterCollector(), onChanged);
  }

  @Override
  public @NotNull String filterControlSymbols(@NotNull String pattern) {
    if (pattern.indexOf('#') != -1) {
      pattern = applyPatternFilter(pattern, ourPatternToDetectMembers);
    }

    if (pattern.indexOf('$') != -1) {
      pattern = applyPatternFilter(pattern, ourPatternToDetectAnonymousClasses);
    }

    return super.filterControlSymbols(pattern);
  }

  @Override
  public boolean isEmptyPatternSupported() {
    return true;
  }

  @Override
  public int getElementPriority(@NotNull Object element, @NotNull String searchPattern) {
    return super.getElementPriority(element, searchPattern) + 5;
  }

  @Override
  public @Nullable ExtendedInfo createExtendedInfo() {
    return createPsiExtendedInfo();
  }

  @Override
  protected @Nullable Navigatable createExtendedNavigatable(PsiElement psi, String searchText, int modifiers) {
    Navigatable res = super.createExtendedNavigatable(psi, searchText, modifiers);
    if (res != null) {
      return res;
    }

    VirtualFile file = PsiUtilCore.getVirtualFile(psi);
    String memberName = getMemberName(searchText);
    if (file != null && memberName != null) {
      Navigatable delegate = findMember(memberName, searchText, psi, file);
      if (delegate != null) {
        return new Navigatable() {
          @Override
          public void navigate(boolean requestFocus) {
            NavigationUtil.activateFileWithPsiElement(psi, false);
            delegate.navigate(true);

          }

          @Override
          public boolean canNavigate() {
            return delegate.canNavigate();
          }

          @Override
          public boolean canNavigateToSource() {
            return delegate.canNavigateToSource();
          }
        };
      }
    }

    return null;
  }

  public static @Nullable String pathToAnonymousClass(Matcher matcher) {
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

  private static String getMemberName(String searchedText) {
    final int index = searchedText.lastIndexOf('#');
    if (index == -1) {
      return null;
    }

    String name = searchedText.substring(index + 1).trim();
    return StringUtil.isEmpty(name) ? null : name;
  }

  public static @Nullable Navigatable findMember(String memberPattern, String fullPattern, PsiElement psiElement, VirtualFile file) {
    final PsiStructureViewFactory factory = LanguageStructureViewBuilder.INSTANCE.forLanguage(psiElement.getLanguage());
    final StructureViewBuilder builder = factory == null ? null : factory.getStructureViewBuilder(psiElement.getContainingFile());
    final FileEditor[] editors = FileEditorManager.getInstance(psiElement.getProject()).getEditors(file);
    if (builder == null || editors.length == 0) {
      return null;
    }

    final StructureView view = builder.createStructureView(editors[0], psiElement.getProject());
    try {
      final StructureViewTreeElement element = findElement(view.getTreeModel().getRoot(), psiElement, 4);
      if (element == null) {
        return null;
      }

      MinusculeMatcher matcher = NameUtil.buildMatcher(memberPattern).build();
      int max = Integer.MIN_VALUE;
      Object target = null;
      for (TreeElement treeElement : element.getChildren()) {
        if (treeElement instanceof StructureViewTreeElement) {
          Object value = ((StructureViewTreeElement)treeElement).getValue();
          if (value instanceof PsiElement && value instanceof Navigatable &&
              fullPattern.equals(CopyReferenceAction.elementToFqn((PsiElement)value))) {
            return (Navigatable)value;
          }

          String presentableText = treeElement.getPresentation().getPresentableText();
          if (presentableText != null) {
            final int degree = matcher.matchingDegree(presentableText);
            if (degree > max) {
              max = degree;
              target = ((StructureViewTreeElement)treeElement).getValue();
            }
          }
        }
      }
      return target instanceof Navigatable ? (Navigatable)target : null;
    }
    finally {
      Disposer.dispose(view);
    }
  }

  private static @Nullable StructureViewTreeElement findElement(StructureViewTreeElement node, PsiElement element, int hopes) {
    Object value = node.getValue();
    if (value instanceof PsiElement) {
      if (((PsiElement)value).isEquivalentTo(element)) {
        return node;
      }
      if (hopes != 0) {
        for (TreeElement child : node.getChildren()) {
          if (child instanceof StructureViewTreeElement) {
            StructureViewTreeElement e = findElement((StructureViewTreeElement)child, element, hopes - 1);
            if (e != null) {
              return e;
            }
          }
        }
      }
    }
    return null;
  }

  public static final class Factory implements SearchEverywhereContributorFactory<Object> {
    @Override
    public @NotNull SearchEverywhereContributor<Object> createContributor(@NotNull AnActionEvent initEvent) {
      return PSIPresentationBgRendererWrapper.wrapIfNecessary(new ClassSearchEverywhereContributor(initEvent));
    }
  }

  static @NotNull PersistentSearchEverywhereContributorFilter<LanguageRef> createLanguageFilter(@NotNull Project project) {
    List<LanguageRef> items = LanguageRef.forAllLanguages();
    GotoClassSymbolConfiguration persistentConfig = GotoClassSymbolConfiguration.getInstance(project);
    return new PersistentSearchEverywhereContributorFilter<>(items, persistentConfig, LanguageRef::getDisplayName, LanguageRef::getIcon);
  }
}
