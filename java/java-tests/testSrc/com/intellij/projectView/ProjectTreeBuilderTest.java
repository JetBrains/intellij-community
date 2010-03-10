package com.intellij.projectView;

import com.intellij.ide.projectView.impl.AbstractProjectViewPSIPane;
import com.intellij.ide.structureView.impl.java.InheritedMembersFilter;
import com.intellij.ide.structureView.newStructureView.StructureViewComponent;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ui.tree.TreeUtil;

import java.io.IOException;

public class ProjectTreeBuilderTest extends BaseProjectViewTestCase {
  public void testStandardProviders() throws Exception {
    getProjectTreeStructure().setProviders();

    final PsiClass aClass = JavaDirectoryService.getInstance().getClasses(getPackageDirectory())[0];

    PsiFile element = aClass.getContainingFile();

    checkNavigateFromSourceBehaviour(element, element.getVirtualFile(), createPane());
  }

  public void testShowClassMembers() throws IncorrectOperationException, IOException {
    myShowMembers = true;
    useStandardProviders();
    PsiClass aClass = JavaDirectoryService.getInstance().getClasses(getPackageDirectory())[1];
    PsiClass innerClass1 = aClass.getInnerClasses()[0];
    PsiClass innerClass12 = innerClass1.getInnerClasses()[0];
    PsiClass innerClass13 = innerClass12.getInnerClasses()[0];
    PsiClass innerClass14 = innerClass13.getInnerClasses()[0];
    PsiClass innerClass15 = innerClass14.getInnerClasses()[0];

    PsiClass innerClass2 = aClass.getInnerClasses()[1];
    PsiClass innerClass21 = innerClass2.getInnerClasses()[0];
    PsiClass innerClass23 = innerClass21.getInnerClasses()[0];
    PsiClass innerClass24 = innerClass23.getInnerClasses()[0];

    PsiField innerClass1Field = innerClass14.getFields()[0];
    PsiField innerClass2Field = innerClass24.getFields()[0];

    final AbstractProjectViewPSIPane pane = createPane();

    checkNavigateFromSourceBehaviour(innerClass2Field, innerClass2Field.getContainingFile().getVirtualFile(), pane);

    IdeaTestUtil.assertTreeEqual(pane.getTree(), "-Project\n" +
                                             " -PsiDirectory: showClassMembers\n" +
                                             "  -PsiDirectory: src\n" +
                                             "   -PsiDirectory: com\n" +
                                             "    -PsiDirectory: package1\n" +
                                             "     +Class1\n" +
                                             "     -Class2\n" +
                                             "      +InnerClass1\n" +
                                             "      -InnerClass2\n" +
                                             "       -InnerClass22\n" +
                                             "        -InnerClass23\n" +
                                             "         -InnerClass24\n" +
                                             "          +InnerClass25\n" +
                                             "          myFieldToSelect:int\n" +
                                             "         myInnerClassField:int\n" +
                                             "        myInnerClassField:int\n" +
                                             "       myInnerClassField:int\n" +
                                             "      getValue():int\n" +
                                             "      myField1:boolean\n" +
                                             "      myField2:boolean\n" +
                                             "      myField3:boolean\n" +
                                             "      myField4:boolean\n" +
                                             getRootFiles() +
                                             " +External Libraries\n"
    );

    assertFalse(isExpanded(innerClass15.getFields()[0], pane));
    assertFalse(isExpanded(innerClass1Field, pane));
    assertTrue(isExpanded(innerClass2Field, pane));

    VirtualFile virtualFile = aClass.getContainingFile().getVirtualFile();
    FileEditorManager fileEditorManager = FileEditorManager.getInstance(myProject);
    FileEditor[] fileEditors = fileEditorManager.openFile(virtualFile, false);
    StructureViewComponent structureViewComponent2 = null;
    StructureViewComponent structureViewComponent = null;

    try {
      structureViewComponent = (StructureViewComponent)fileEditors[0].getStructureViewBuilder().createStructureView(fileEditors[0], myProject);
      structureViewComponent.setActionActive(InheritedMembersFilter.ID, true);

      TreeUtil.collapseAll(structureViewComponent.getTree(), -1);

      structureViewComponent.select(innerClass2Field, true);

      String expected = "-Class2.java\n" +
                  " -Class2\n" +
                  "  +InnerClass1\n" +
                  "  -InnerClass2\n" +
                  "   -InnerClass22\n" +
                  "    -InnerClass23\n" +
                  "     -InnerClass24\n" +
                  "      +InnerClass25\n" +
                  "      myFieldToSelect:int\n" +
                  "     myInnerClassField:int\n" +
                  "    myInnerClassField:int\n" +
                  "   myInnerClassField:int\n" +
                  "  getValue():int\n" +
                  "  myField1:boolean\n" +
                  "  myField2:boolean\n" +
                  "  myField3:boolean\n" +
                  "  myField4:boolean\n";

      IdeaTestUtil.assertTreeEqual(structureViewComponent.getTree(),
                                   expected);


      Disposer.dispose(structureViewComponent);

      final FileEditor fileEditor = fileEditors[0];
      structureViewComponent2 = (StructureViewComponent)fileEditor.getStructureViewBuilder().createStructureView(fileEditor, myProject);
      structureViewComponent2.setActionActive(InheritedMembersFilter.ID, true);
      IdeaTestUtil.assertTreeEqual(structureViewComponent2.getTree(), expected);
    }
    finally {
      fileEditorManager.closeFile(virtualFile);
      if (structureViewComponent2 != null) {
        Disposer.dispose(structureViewComponent2);
      }
    }
  }
}
