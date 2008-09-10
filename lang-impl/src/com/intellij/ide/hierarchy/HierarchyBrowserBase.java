package com.intellij.ide.hierarchy;

import com.intellij.ide.actions.CloseTabToolbarAction;
import com.intellij.ide.actions.ContextHelpAction;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.actionSystem.*;
import com.intellij.pom.Navigatable;
import com.intellij.psi.NavigatablePsiElement;
import com.intellij.psi.PsiElement;
import com.intellij.ui.content.Content;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.util.ArrayList;

/**
 * @author yole
 */
public abstract class HierarchyBrowserBase implements HierarchyBrowser, Disposable, DataProvider {
  protected Content myContent;

  private SimpleToolWindowPanel myComponent = new SimpleToolWindowPanel(true, true);

  public void setContent(final Content content) {
    myContent = content;
  }

  public JComponent getComponent() {
    return myComponent;
  }

  protected void buildUi(JComponent toolbar, JComponent content) {
    myComponent.setToolbar(toolbar);
    myComponent.setContent(content);
  }

  public void dispose() {
  }

  protected ActionToolbar createToolbar(final String place, final String helpID) {
    final DefaultActionGroup actionGroup = new DefaultActionGroup();
    addSpecificActions(actionGroup);
    actionGroup.add(new CloseAction());
    if (helpID != null) {
      actionGroup.add(new ContextHelpAction(helpID));
    }

    return ActionManager.getInstance().createActionToolbar(place, actionGroup, true);
  }

  protected void addSpecificActions(DefaultActionGroup actionGroup) {
  }

  protected abstract JTree getCurrentTree();

  @Nullable
  protected TreePath getSelectedPath() {
    final JTree tree = getCurrentTree();
    if (tree == null) return null;
    return tree.getSelectionPath();
  }

  @Nullable
  protected PsiElement extractPsiElement(final TreePath path) {
    if (path == null) return null;
    final Object lastPathComponent = path.getLastPathComponent();
    if (!(lastPathComponent instanceof DefaultMutableTreeNode)) return null;
    final Object userObject = ((DefaultMutableTreeNode)lastPathComponent).getUserObject();
    return getPsiElementFromNodeDescriptor(userObject);
  }

  @Nullable
  protected abstract PsiElement getPsiElementFromNodeDescriptor(Object userObject);

  @Nullable
  protected PsiElement getSelectedElement() {
    final TreePath path = getSelectedPath();
    return extractPsiElement(path);
  }

  protected PsiElement[] getSelectedElements() {
    JTree currentTree = getCurrentTree();
    if (currentTree == null) return PsiElement.EMPTY_ARRAY;
    TreePath[] paths = currentTree.getSelectionPaths();
    if (paths == null) return PsiElement.EMPTY_ARRAY;
    ArrayList<PsiElement> psiElements = new ArrayList<PsiElement>();
    for (TreePath path : paths) {
      PsiElement psiElement = extractPsiElement(path);
      if (psiElement == null || !psiElement.isValid()) continue;
      psiElements.add(psiElement);
    }
    return psiElements.toArray(new PsiElement[psiElements.size()]);
  }

  @Nullable
  protected Navigatable[] getNavigatables() {
    final PsiElement[] objects = getSelectedElements();
    if (objects == null || objects.length == 0) return null;
    final ArrayList<Navigatable> result = new ArrayList<Navigatable>();
    for (final PsiElement element : objects) {
      if (element.isValid() && element instanceof NavigatablePsiElement) {
        result.add((NavigatablePsiElement) element);
      }
    }
    return result.toArray(new Navigatable[result.size()]);
  }

  public Object getData(@NonNls final String dataId) {
    if (DataConstants.PSI_ELEMENT.equals(dataId)) {
      final PsiElement anElement = getSelectedElement();
      return anElement != null && anElement.isValid() ? anElement : null;
    }
    if (DataConstants.NAVIGATABLE_ARRAY.equals(dataId)) {
      return getNavigatables();
    }
    if (DataConstants.PSI_ELEMENT_ARRAY.equals(dataId)) {
      return getSelectedElements();
    }
    return null;
  }

  public final class CloseAction extends CloseTabToolbarAction {
    public final void actionPerformed(final AnActionEvent e) {
      myContent.getManager().removeContent(myContent, true);
    }
  }
}
