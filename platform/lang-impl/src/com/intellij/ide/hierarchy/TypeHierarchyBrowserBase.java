/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.ide.hierarchy;

import com.intellij.history.LocalHistory;
import com.intellij.history.LocalHistoryAction;
import com.intellij.ide.DeleteProvider;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.DeleteHandler;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

public abstract class TypeHierarchyBrowserBase extends HierarchyBrowserBaseEx {

  @SuppressWarnings({"UnresolvedPropertyKey"})
  public static final String TYPE_HIERARCHY_TYPE = IdeBundle.message("title.hierarchy.class");
  @SuppressWarnings({"UnresolvedPropertyKey"})
  public static final String SUBTYPES_HIERARCHY_TYPE = IdeBundle.message("title.hierarchy.subtypes");
  @SuppressWarnings({"UnresolvedPropertyKey"})
  public static final String SUPERTYPES_HIERARCHY_TYPE = IdeBundle.message("title.hierarchy.supertypes");

  private boolean myIsInterface;

  private final MyDeleteProvider myDeleteElementProvider = new MyDeleteProvider();

  public static final DataKey<TypeHierarchyBrowserBase> DATA_KEY = DataKey.create("com.intellij.ide.hierarchy.TypeHierarchyBrowserBase");
  @Deprecated public static final String TYPE_HIERARCHY_BROWSER_DATA_KEY = DATA_KEY.getName();

  public TypeHierarchyBrowserBase(final Project project, final PsiElement element) {
    super(project, element);
  }

  protected abstract boolean isInterface(PsiElement psiElement);

  protected abstract boolean canBeDeleted(PsiElement psiElement);

  protected abstract String getQualifiedName(PsiElement psiElement);

  public boolean isInterface() {
    return myIsInterface;
  }

  @Override
  protected void setHierarchyBase(PsiElement element) {
    super.setHierarchyBase(element);
    myIsInterface = isInterface(element);
  }

  protected void prependActions(final DefaultActionGroup actionGroup) {
    actionGroup.add(new ViewClassHierarchyAction());
    actionGroup.add(new ViewSupertypesHierarchyAction());
    actionGroup.add(new ViewSubtypesHierarchyAction());
    actionGroup.add(new AlphaSortAction());
  }

  @NotNull
  protected String getBrowserDataKey() {
    return DATA_KEY.getName();
  }

  @NotNull
  protected String getActionPlace() {
    return ActionPlaces.TYPE_HIERARCHY_VIEW_TOOLBAR;
  }

  public final Object getData(final String dataId) {
    if (PlatformDataKeys.DELETE_ELEMENT_PROVIDER.is(dataId)) {
      return myDeleteElementProvider;
    }
    return super.getData(dataId);
  }

  @NotNull
  protected String getPrevOccurenceActionNameImpl() {
    return IdeBundle.message("hierarchy.type.prev.occurence.name");
  }

  @NotNull
  protected String getNextOccurenceActionNameImpl() {
    return IdeBundle.message("hierarchy.type.next.occurence.name");
  }

  private final class MyDeleteProvider implements DeleteProvider {
    public final void deleteElement(final DataContext dataContext) {
      final PsiElement aClass = getSelectedElement();
      if (!canBeDeleted(aClass)) return;
      LocalHistoryAction a = LocalHistory.getInstance().startAction(IdeBundle.message("progress.deleting.class", getQualifiedName(aClass)));
      try {
        final PsiElement[] elements = new PsiElement[]{aClass};
        DeleteHandler.deletePsiElement(elements, myProject);
      }
      finally {
        a.finish();
      }
    }

    public final boolean canDeleteElement(final DataContext dataContext) {
      final PsiElement aClass = getSelectedElement();
      if (!canBeDeleted(aClass)) {
        return false;
      }
      final PsiElement[] elements = new PsiElement[]{aClass};
      return DeleteHandler.shouldEnableDeleteAction(elements);
    }
  }


  protected static class BaseOnThisTypeAction extends BaseOnThisElementAction {

    public BaseOnThisTypeAction() {
      super("", IdeActions.ACTION_TYPE_HIERARCHY, DATA_KEY.getName());
    }

    @Override
    protected String correctViewType(HierarchyBrowserBaseEx browser, String viewType) {
      if (((TypeHierarchyBrowserBase)browser).myIsInterface && TYPE_HIERARCHY_TYPE.equals(viewType)) return SUBTYPES_HIERARCHY_TYPE;
      return viewType;
    }

    @Override
    protected String getNonDefaultText(@NotNull HierarchyBrowserBaseEx browser, @NotNull PsiElement element) {
      return ((TypeHierarchyBrowserBase)browser).isInterface(element)
             ? IdeBundle.message("action.base.on.this.interface")
             : IdeBundle.message("action.base.on.this.class");
    }
  }

}
