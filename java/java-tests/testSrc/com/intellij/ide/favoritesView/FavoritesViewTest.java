// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.favoritesView;

import com.intellij.ide.favoritesTreeView.FavoritesListNode;
import com.intellij.ide.favoritesTreeView.FavoritesManager;
import com.intellij.ide.projectView.impl.AbstractUrl;
import com.intellij.ide.projectView.impl.nodes.PackageElement;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.TestSourceBasedTestCase;
import com.intellij.util.TreeItem;
import org.jdom.Element;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class FavoritesViewTest extends TestSourceBasedTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    String root = PathManagerEx.getTestDataPath() + "/ide/favoritesView/" + getTestName(true);
    PsiTestUtil.removeAllRoots(myModule, IdeaTestUtil.getMockJdk17());
    createTestProjectStructure(root);
  }

  @Override
  protected String getTestPath() {
    return "ide" + File.separator + "favoritesView";
  }

  public void testClass() {
    final JavaPsiFacade psiManager = getJavaFacade();
    final PsiClass aClass = psiManager.findClass("com.a", GlobalSearchScope.allScope(myProject));
    checkAddAndRestored(aClass);
  }

  public void testDirectory() {
    final JavaPsiFacade psiManager = getJavaFacade();
    final PsiDirectory psiDirectory = psiManager.findClass("dir.a", GlobalSearchScope.allScope(myProject)).getContainingFile().getContainingDirectory();
    checkAddAndRestored(psiDirectory);
  }

  public void testPackage() {
    final PsiManager psiManager = PsiManager.getInstance(myProject);
    final PsiPackage aPackage = JavaPsiFacade.getInstance(psiManager.getProject()).findPackage("pack");
    PackageElement packageElement = new PackageElement(myModule, aPackage, false);
    checkAddAndRestored(packageElement);
  }

  private void checkAddAndRestored(Object nodeToAdd) {
    FavoritesManager favoritesManager = FavoritesManager.getInstance(getProject());
    favoritesManager.createNewList("xxx");
    List<String> names = favoritesManager.getAvailableFavoritesListNames();
    assertEquals(1, names.size());
    String name = names.get(0);
    assertEquals("xxx", name);
    assertTrue(favoritesManager.getFavoritesListRootUrls(name).isEmpty());
    assertTrue(favoritesManager.addRoots(name, getModule(), nodeToAdd));

    assertTrue(!favoritesManager.getFavoritesListRootUrls(name).isEmpty());
    Element favorite = new Element("favorite");
    favoritesManager.writeExternal(favorite);

    final Collection<AbstractTreeNode> nodes = FavoritesListNode.getFavoritesRoots(myProject, "xxx", null);
    AbstractTreeNode targetNode = null;
    final AbstractUrl url = FavoritesManager.createUrlByElement(nodeToAdd, myProject);
    for (AbstractTreeNode node : nodes) {
      final AbstractUrl nodeUrl = FavoritesManager.createUrlByElement(node.getValue(), myProject);
      if (Comparing.equal(nodeUrl, url)) {
        targetNode = node;
        break;
      }
    }
    assertNotNull(targetNode);
    targetNode.setParent(new FavoritesListNode(myProject, "xxx"));
    assertTrue(favoritesManager.removeRoot(name, Collections.singletonList(targetNode)));
    assertTrue(favoritesManager.getFavoritesListRootUrls(name).isEmpty());

    favoritesManager.readExternal(favorite);
    Collection<TreeItem<Pair<AbstractUrl,String>>> urls = favoritesManager.getFavoritesListRootUrls(name);
    assertEquals(1, urls.size());

    AbstractUrl abstractUrl = urls.iterator().next().getData().getFirst();
    Object[] path = abstractUrl.createPath(getProject());
    assertTrue(path.length != 0);
    Object value = path[path.length - 1];
    assertEquals(nodeToAdd, value);
  }
}
