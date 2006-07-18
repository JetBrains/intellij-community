/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Apr 11, 2002
 * Time: 5:18:36 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.testFramework;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.GlobalInspectionTool;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ex.*;
import com.intellij.codeInspection.reference.RefManagerImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiManager;
import org.jdom.Document;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import java.io.CharArrayReader;
import java.io.File;
import java.io.StreamTokenizer;
import java.util.ArrayList;

@SuppressWarnings({"HardCodedStringLiteral"})
public abstract class InspectionTestCase extends PsiTestCase {
  private static final Logger LOG = Logger.getInstance("#com.intellij.testFramework.InspectionTestCase");

  public InspectionManagerEx getManager() {
    return (InspectionManagerEx) InspectionManager.getInstance(myProject);
  }

  public void doTest(@NonNls String folderName, LocalInspectionTool tool) throws Exception {
    doTest(folderName, new LocalInspectionToolWrapper(tool));
  }
  public void doTest(@NonNls String folderName, GlobalInspectionTool tool) throws Exception {
    doTest(folderName, new GlobalInspectionToolWrapper(tool));
  }
  public void doTest(@NonNls String folderName, GlobalInspectionTool tool, boolean checkRange) throws Exception {
    doTest(folderName, new GlobalInspectionToolWrapper(tool), checkRange);
  }
  public void doTest(@NonNls String folderName, InspectionTool tool) throws Exception {
    doTest(folderName, tool, "java 1.4");
  }

  public void doTest(@NonNls String folderName, InspectionTool tool, final boolean checkRange) throws Exception {
    doTest(folderName, tool, "java 1.4", checkRange);
  }

  public void doTest(@NonNls String folderName, InspectionTool tool, @NonNls final String jdkName) throws Exception {
    doTest(folderName, tool, jdkName, false);
  }

  public void doTest(@NonNls String folderName, InspectionTool tool, @NonNls final String jdkName, boolean checkRange) throws Exception {
    final String testDir = getTestDataPath() + "/"+folderName;
    final VirtualFile[] sourceDir = new VirtualFile[1];
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        try {
          VirtualFile projectDir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(new File(testDir));
          assertNotNull(projectDir);
          sourceDir[0] = projectDir.findChild("src");
          if (sourceDir[0] == null) {
            sourceDir[0] = projectDir;
          }
          VirtualFile ext_src = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(new File(testDir + "/ext_src"));
          final ModuleRootManager rootManager = ModuleRootManager.getInstance(myModule);
          final ModifiableRootModel rootModel = rootManager.getModifiableModel();
          rootModel.clear();
          // configure source and output path
          final ContentEntry contentEntry = rootModel.addContentEntry(projectDir);
          contentEntry.addSourceFolder(sourceDir[0], false);
          if (ext_src != null) {
            contentEntry.addSourceFolder(ext_src, false);
          }

          // IMPORTANT! The jdk must be obtained in a way it is obtained in the normal program!
          //ProjectJdkEx jdk = ProjectJdkTable.getInstance().getInternalJdk();
          ProjectJdk jdk;
          if ("java 1.5".equals(jdkName)) {
            jdk = JavaSdkImpl.getMockJdk15(jdkName);
            myPsiManager.setEffectiveLanguageLevel(LanguageLevel.JDK_1_5);
          }
          else {
            jdk = JavaSdkImpl.getMockJdk(jdkName);
          }

          rootModel.setJdk(jdk);

          rootModel.commit();
        } catch (Exception e) {
          LOG.error(e);
        }
      }
    });

    final Element root = new Element("problems");
    final Document doc = new Document(root);

    PsiManager psiManager = PsiManager.getInstance(myProject);
    AnalysisScope scope = new AnalysisScope(psiManager.findDirectory(sourceDir[0]));
    InspectionManagerEx inspectionManager = (InspectionManagerEx) InspectionManager.getInstance(myProject);
    final GlobalInspectionContextImpl globalContext = inspectionManager.createNewGlobalContext(true);
    globalContext.setCurrentScope(scope);

    tool.initialize(globalContext);
    if (tool.isGraphNeeded()){
      ((RefManagerImpl)tool.getRefManager()).findAllDeclarations();
    }

    tool.runInspection(scope, inspectionManager);

    tool.queryExternalUsagesRequests(inspectionManager);

    do {
      globalContext.processSearchRequests();
    } while (tool.queryExternalUsagesRequests(inspectionManager));

    tool.exportResults(root);

    File file = new File(testDir + "/expected.xml");
    Document expectedDocument = JDOMUtil.loadDocument(file);

    compareWithExpected(expectedDocument, doc, checkRange);
  }

  @NonNls
  protected String getTestDataPath() {
    return PathManagerEx.getTestDataPath()+"/inspection/";
  }

  private static void compareWithExpected(Document expectedDoc, Document doc, boolean checkRange) throws Exception {
    ArrayList<Object> expectedProblems = new ArrayList<Object>(expectedDoc.getRootElement().getChildren("problem"));
    ArrayList reportedProblems = new ArrayList(doc.getRootElement().getChildren("problem"));

    Element[] expectedArrayed = expectedProblems.toArray(new Element[expectedProblems.size()]);
    boolean failed = false;

expected:
    for (Element expectedProblem : expectedArrayed) {
      Element[] reportedArrayed = (Element[])reportedProblems.toArray(new Element[reportedProblems.size()]);
      for (Element reportedProblem : reportedArrayed) {
        if (compareProblemWithExpected(reportedProblem, expectedProblem, checkRange)) {
          expectedProblems.remove(expectedProblem);
          reportedProblems.remove(reportedProblem);
          continue expected;
        }
      }

      Document missing = new Document((Element)expectedProblem.clone());
      System.out.println("The following haven't been reported as expected: " + new String(JDOMUtil.printDocument(missing, "\n")));
      failed = true;
    }

    for (Object reportedProblem1 : reportedProblems) {
      Element reportedProblem = (Element)reportedProblem1;
      Document extra = new Document((Element)reportedProblem.clone());
      System.out.println("The following has been unexpectedly reported: " + new String(JDOMUtil.printDocument(extra, "\n")));
      failed = true;
    }

    assertFalse(failed);
  }

  private static boolean compareProblemWithExpected(Element reportedProblem, Element expectedProblem, boolean checkRange) throws Exception {
    if (!compareFiles(reportedProblem, expectedProblem)) return false;
    if (!compareLines(reportedProblem, expectedProblem)) return false;
    if (!compareDescriptions(reportedProblem, expectedProblem)) return false;
    if (checkRange && !compareTextRange(reportedProblem, expectedProblem)) return false;
    return true;
  }

  private static boolean compareTextRange(final Element reportedProblem, final Element expectedProblem) {
    Element reportedTextRange = reportedProblem.getChild("text_range");
    if (reportedTextRange == null) return false;
    Element expectedTextRange = expectedProblem.getChild("text_range");
    return Comparing.equal(reportedTextRange.getAttributeValue("end"), expectedTextRange.getAttributeValue("end")) &&
           Comparing.equal(reportedTextRange.getAttributeValue("start"), expectedTextRange.getAttributeValue("start"));
  }

  private static boolean compareDescriptions(Element reportedProblem, Element expectedProblem) throws Exception {
    String expectedDescription = expectedProblem.getChildText("description");
    String reportedDescription = reportedProblem.getChildText("description");
    if (expectedDescription.equals(reportedDescription)) return true;
    
    StreamTokenizer tokenizer = new StreamTokenizer(new CharArrayReader(expectedDescription.toCharArray()));
    tokenizer.quoteChar('\'');

    int idx = 0;
    while (tokenizer.nextToken() != StreamTokenizer.TT_EOF) {
      String word;
      if (tokenizer.sval != null) {
        word = tokenizer.sval;
      } else if (tokenizer.ttype == StreamTokenizer.TT_NUMBER) {
        word = Double.toString(tokenizer.nval);
      }
      else {
        continue;
      }

      idx = reportedDescription.indexOf(word, idx);
      if (idx == -1) return false;
      idx += word.length();
    }

    return true;
  }

  private static boolean compareLines(Element reportedProblem, Element expectedProblem) {
    return Comparing.equal(reportedProblem.getChildText("line"), expectedProblem.getChildText("line"));
  }

  private static boolean compareFiles(Element reportedProblem, Element expectedProblem) {
    String reportedFileName = reportedProblem.getChildText("file");
    File reportedFile = new File(reportedFileName);

    return Comparing.equal(reportedFile.getName(), expectedProblem.getChildText("file"));
  }
}
