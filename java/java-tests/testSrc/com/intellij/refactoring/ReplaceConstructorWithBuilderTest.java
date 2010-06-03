/*
 * User: anna
 * Date: 06-May-2008
 */
package com.intellij.refactoring;

import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.replaceConstructorWithBuilder.ParameterData;
import com.intellij.refactoring.replaceConstructorWithBuilder.ReplaceConstructorWithBuilderProcessor;
import com.intellij.util.containers.HashMap;
import com.intellij.JavaTestUtil;

import java.util.LinkedHashMap;
import java.util.Map;

public class ReplaceConstructorWithBuilderTest extends MultiFileTestCase {
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  public void testVarargs() throws Exception {
    doTest(true);
  }

  public void testExistingEmptyBuilder() throws Exception {
    doTest(false);
  }

  public void testMultipleParams() throws Exception {
    doTest(true);
  }

  public void testExistingHalfEmptyBuilder() throws Exception {
    doTest(false);
  }

  public void testExistingVoidSettersBuilder() throws Exception {
    doTest(false);
  }

  public void testConstructorChain() throws Exception {
    final HashMap<String, String> defaults = new HashMap<String, String>();
    defaults.put("i", "2");
    doTest(true, defaults);
  }

  public void testConstructorTree() throws Exception {
    doTest(true, null, "Found constructors are not reducible to simple chain");
  }

  public void testGenerics() throws Exception {
    doTest(true);
  }

  public void testImports() throws Exception {
    doTest(true, null, null, "foo");
  }

  private void doTest(final boolean createNewBuilderClass) throws Exception {
    doTest(createNewBuilderClass, null);
  }

  private void doTest(final boolean createNewBuilderClass, final Map<String, String> expectedDefaults) throws Exception {
    doTest(createNewBuilderClass, expectedDefaults, null);
  }

  private void doTest(final boolean createNewBuilderClass, final Map<String, String> expectedDefaults, final String conflicts) throws Exception {
    doTest(createNewBuilderClass, expectedDefaults, conflicts, "");
  }

  private void doTest(final boolean createNewBuilderClass,
                      final Map<String, String> expectedDefaults,
                      final String conflicts,
                      final String packageName) throws Exception {
    doTest(new PerformAction() {
      public void performAction(final VirtualFile rootDir, final VirtualFile rootAfter) throws Exception {
        final PsiClass aClass = myJavaFacade.findClass("Test", GlobalSearchScope.projectScope(getProject()));
        assertNotNull("Class Test not found", aClass);

        final LinkedHashMap<String, ParameterData> map = new LinkedHashMap<String, ParameterData>();
        final PsiMethod[] constructors = aClass.getConstructors();
        for (PsiMethod constructor : constructors) {
          ParameterData.createFromConstructor(constructor, map);
        }
        if (expectedDefaults != null) {
          for (Map.Entry<String, String> entry : expectedDefaults.entrySet()) {
            final ParameterData parameterData = map.get(entry.getKey());
            assertNotNull(parameterData);
            assertEquals(entry.getValue(), parameterData.getDefaultValue());
          }
        }
        try {
          new ReplaceConstructorWithBuilderProcessor(getProject(), constructors, map, "Builder", packageName, createNewBuilderClass).run();
          if (conflicts != null) {
            fail("Conflicts were not detected:" + conflicts);
          }
        }
        catch (BaseRefactoringProcessor.ConflictsInTestsException e) {

          if (conflicts == null) {
            fail("Conflict detected:" + e.getMessage());
          }
        }
        LocalFileSystem.getInstance().refresh(false);
        FileDocumentManager.getInstance().saveAllDocuments();
      }
    });
  }


  protected String getTestRoot() {
    return "/refactoring/replaceConstructorWithBuilder/";
  }
}
