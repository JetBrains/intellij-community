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

/*
 * User: anna
 * Date: 14-Jan-2007
 */
package com.intellij.codeInspection;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.actions.ViewOfflineResultsAction;
import com.intellij.codeInspection.defUse.DefUseInspection;
import com.intellij.codeInspection.defUse.DefUseInspectionBase;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.codeInspection.ex.ToolsImpl;
import com.intellij.codeInspection.offline.OfflineProblemDescriptor;
import com.intellij.codeInspection.offlineViewer.OfflineViewParseUtil;
import com.intellij.codeInspection.ui.InspectionResultsView;
import com.intellij.codeInspection.ui.InspectionTree;
import com.intellij.codeInspection.ui.InspectionTreeNode;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.TestSourceBasedTestCase;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class OfflineIRVTest extends TestSourceBasedTestCase {
  private InspectionResultsView myView;
  private LocalInspectionToolWrapper myToolWrapper;

  private static String varMessage(String name) {
    return InspectionsBundle.message("inspection.unused.assignment.problem.descriptor1", "<code>"+name+"</code>") + ".";
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    HighlightDisplayKey key = HighlightDisplayKey.find(DefUseInspectionBase.SHORT_NAME);
    if (key == null) {
      HighlightDisplayKey.register(DefUseInspectionBase.SHORT_NAME);
    }

    myToolWrapper = new LocalInspectionToolWrapper(new DefUseInspection());
    myView = ViewOfflineResultsAction.showOfflineView(getProject(), parse(), new InspectionProfileImpl("test") {
      @Override
      public boolean isToolEnabled(final HighlightDisplayKey key, PsiElement element) {
        return Comparing.strEqual(key.toString(), DefUseInspectionBase.SHORT_NAME);
      }

      @Override
      @NotNull
      public InspectionToolWrapper[] getInspectionTools(PsiElement element) {
        return new InspectionToolWrapper[]{myToolWrapper};
      }

      @Override
      @NotNull
      public ModifiableModel getModifiableModel() {
        return new InspectionProfileImpl("test") {
          @Override
          @NotNull
          public InspectionToolWrapper[] getInspectionTools(PsiElement element) {
            return new InspectionToolWrapper[]{myToolWrapper};
          }

          @Override
          public boolean isToolEnabled(final HighlightDisplayKey key, PsiElement element) {
            return Comparing.strEqual(key.toString(), DefUseInspectionBase.SHORT_NAME);
          }
        };
      }
    }, "");
    myView.getGlobalInspectionContext().getTools().put(
      myToolWrapper.getShortName(), new ToolsImpl(myToolWrapper, myToolWrapper.getDefaultLevel(), true));
    myToolWrapper.initialize(myView.getGlobalInspectionContext());
  }

  private Map<String, Map<String, Set<OfflineProblemDescriptor>>> parse() throws IOException {
    final String moduleName = getModule().getName();
    final Map<String, Map<String, Set<OfflineProblemDescriptor>>> map = new HashMap<String, Map<String, Set<OfflineProblemDescriptor>>>();
    final File res = new File(PathManagerEx.getTestDataPath(), getTestPath() + File.separator + "res");
    final File[] files = res.listFiles();
    assert files != null;
    for (File file : files) {
      final String name = file.getName();
      final String problems = FileUtil.loadFile(file);
      final Map<String, Set<OfflineProblemDescriptor>> descriptors = OfflineViewParseUtil.parse(problems);
      for (Set<OfflineProblemDescriptor> problemDescriptors : descriptors.values()) {
        for (OfflineProblemDescriptor descriptor : problemDescriptors) {
          descriptor.setModule(moduleName);
        }
      }
      map.put(name.substring(0, name.lastIndexOf('.')), descriptors);
    }
    return map;
  }

  @Override
  protected void tearDown() throws Exception {
    Disposer.dispose(myView);
    myView = null;
    myToolWrapper = null;
    super.tearDown();
  }

  public void testOfflineView() throws Exception {
    myView.getGlobalInspectionContext().getUIOptions().SHOW_STRUCTURE = true;
    InspectionTree tree = updateTree();
    TreeUtil.expandAll(tree);
    PlatformTestUtil.assertTreeEqual(tree, "-" + getProject() + "\n"
                                           + " -Probable bugs\n"
                                           + "  -" + myToolWrapper + "\n"
                                           + "   -" + getModule().toString() + "\n"
                                           + "    -<default>\n"
                                           + "     -Test\n"
                                           + "      -foo()\n"
                                           + "       " + varMessage("j") + "\n"
                                           + "      -main(String[])\n"
                                           + "       " + varMessage("test") + "\n"
                                           + "      -f()\n"
                                           + "       -D\n"
                                           + "        -b()\n"
                                           + "         " + InspectionsBundle.message("inspection.unused.assignment.problem.descriptor1", "'" + "r" + "'") + "\n"
                                           + "         -anonymous (java.lang.Runnable)\n"
                                           + "          -run()\n"
                                           + "           " + varMessage("i") + "\n"
                                           + "      -ff()\n"
                                           + "       " + varMessage("d") + "\n"
                                           + "       " + varMessage("a") + "\n");
    myView.getGlobalInspectionContext().getUIOptions().SHOW_STRUCTURE = false;
    tree = updateTree();
    PlatformTestUtil.assertTreeEqual(tree, "-" + getProject() + "\n"
                                           + " -Probable bugs\n"
                                           + "  -" + myToolWrapper + "\n"
                                           + "   -Test\n"
                                           + "    " + varMessage("j") + "\n"
                                           + "    " + varMessage("test") + "\n"
                                           + "    " + varMessage("r") + "\n"
                                           + "    " + varMessage("i") + "\n"
                                           + "    " + varMessage("d") + "\n"
                                           + "    " + varMessage("a") + "\n");
    TreeUtil.selectFirstNode(tree);
    final InspectionTreeNode root = (InspectionTreeNode)tree.getLastSelectedPathComponent();
    root.ignoreElement();
    TreeUtil.traverse(root, new TreeUtil.Traverse() {
      @Override
      public boolean accept(final Object node) {
        assertTrue(((InspectionTreeNode)node).isResolved());
        return true;
      }
    });
    myView.getGlobalInspectionContext().getUIOptions().FILTER_RESOLVED_ITEMS = true;
    tree = updateTree();
    PlatformTestUtil.assertTreeEqual(tree, getProject() + "\n");
    myView.getGlobalInspectionContext().getUIOptions().FILTER_RESOLVED_ITEMS = false;
    tree = updateTree();
    PlatformTestUtil.assertTreeEqual(tree, "-" + getProject() + "\n"
                                           + " -Probable bugs\n"
                                           + "  -" + myToolWrapper + "\n"
                                           + "   -Test\n"
                                           + "    " + varMessage("j") + "\n"
                                           + "    " + varMessage("test") + "\n"
                                           + "    " + varMessage("r") + "\n"
                                           + "    " + varMessage("i") + "\n"
                                           + "    " + varMessage("d") + "\n"
                                           + "    " + varMessage("a") + "\n");
  }

  private InspectionTree updateTree() {
    myView.update();
    final InspectionTree tree = myView.getTree();
    TreeUtil.expandAll(tree);
    return tree;
  }

  @Override
  protected String getTestPath() {
    return "inspection/offline";
  }

  @Override
  protected String getTestDirectoryName() {
    return "project";
  }
}
