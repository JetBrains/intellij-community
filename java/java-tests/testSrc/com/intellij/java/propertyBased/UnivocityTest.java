// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.propertyBased;

import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiDocumentManagerImpl;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.testFramework.SkipSlowTestLocally;
import com.intellij.testFramework.propertyBased.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jetCheck.Generator;
import org.jetbrains.jetCheck.IntDistribution;
import org.jetbrains.jetCheck.PropertyChecker;
import org.junit.Assume;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@SkipSlowTestLocally
public class UnivocityTest extends BaseUnivocityTest {
  @Override
  public void setUp() throws Exception {
    super.setUp();
    ((PsiDocumentManagerImpl)PsiDocumentManager.getInstance(myProject)).disableBackgroundCommit(getTestRootDisposable());
    MadTestingUtil.enableAllInspections(myProject, JavaLanguage.INSTANCE, "GrazieInspection");
  }

  @Override
  public void tearDown() throws Exception {
    try {
      Disposer.dispose(((ProjectEx)myProject).getEarlyDisposable());
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  public void testCompilabilityAfterIntentions() {
    JavaPsiFacade facade = JavaPsiFacade.getInstance(myProject);
    GlobalSearchScope allScope = GlobalSearchScope.allScope(myProject);
    Assume.assumeTrue("Maven import failed",
                      facade.findClass("org.testng.Assert", allScope) != null &&
                      facade.findClass("com.univocity.test.OutputTester", allScope) != null);

    initCompiler();
    PsiModificationTracker tracker = PsiManager.getInstance(myProject).getModificationTracker();

    AtomicLong rebuildStamp = new AtomicLong();

    PropertyChecker.customized()
      .withIterationCount(30).checkScenarios(() -> env -> {
      long startModCount = tracker.getModificationCount();

      if (rebuildStamp.getAndSet(startModCount) != startModCount) {
        checkCompiles(myCompilerTester.rebuild());
      }

      MadTestingUtil.changeAndRevert(myProject, () -> {
        env.executeCommands(Generator.constant(env1 -> {
          PsiJavaFile file = env1.generateValue(psiJavaFiles(), null);
          env1.logMessage("Open " + file.getVirtualFile().getPath() + " in editor");
          if ("Example.java".equals(file.getName())) {
            env1.logMessage("OutputTester class: " + facade.findClass("com.univocity.test.OutputTester", allScope));
            env1.logMessage("OutputTester files: " + FilenameIndex.getVirtualFilesByName("OutputTester.java", allScope));
            env1.logMessage("content roots: " + Arrays.toString(
              ModuleRootManager.getInstance(ModuleManager.getInstance(myProject).getModules()[0]).getSourceRoots(true)));
          }
          env1.executeCommands(IntDistribution.uniform(1, 5), Generator.constant(new InvokeIntention(file, new JavaGreenIntentionPolicy())));
        }));

        if (tracker.getModificationCount() != startModCount) {
          checkCompiles(myCompilerTester.make());
        }
      });
    });
  }

  public void testRandomActivity() {
    RecursionManager.disableMissedCacheAssertions(getTestRootDisposable());
    Generator<PsiJavaFile> javaFiles = psiJavaFiles();
    initCompiler();
    PropertyChecker.customized()
      .withIterationCount(30).checkScenarios(() -> env ->
      MadTestingUtil.changeAndRevert(myProject, () ->
        env.executeCommands(Generator.constant(env1 -> {
          PsiJavaFile file = env1.generateValue(javaFiles, "Open %s in editor");

          LinkedHashMap<Generator<? extends MadTestingAction>, Integer> actionWeights = new LinkedHashMap<>();

          List<ActionOnFile> psiMutations = Arrays.asList(new DeleteRange(file),
                                                          new AddNullArgument(file),
                                                          new DeleteForeachInitializers(file),
                                                          new DeleteSecondArgument(file),
                                                          new MakeAllMethodsVoid(file));
          for (ActionOnFile mutation : psiMutations) {
            actionWeights.put(Generator.constant(mutation), 1);
          }

          actionWeights.put(Generator.constant(new InvokeCompletion(file, new JavaCompletionPolicy())), 1);
          actionWeights.put(Generator.constant(new RehighlightAllEditors(myProject)), 2);
          actionWeights.put(javaFiles.flatMap(f -> Generator.sampledFrom(new FilePropertiesChanged(f), new AddImportExternally(f))), 5);
          actionWeights.put(Generator.constant(new InvokeIntention(file, new JavaIntentionPolicy())), 10);

          env1.executeCommands(IntDistribution.uniform(1, 7), Generator.frequency(actionWeights));
        }))));
  }

  private static class AddImportExternally extends ActionOnFile {
    AddImportExternally(PsiJavaFile file) {super(file);}

    @Override
    public void performCommand(@NotNull Environment env) {
      PsiDocumentManager.getInstance(getProject()).commitDocument(getDocument());
      PsiFile file = getFile();
      if (file instanceof PsiJavaFile) {
        PsiImportList importList = ((PsiJavaFile)file).getImportList();
        if (importList != null) {
          PsiImportStatement[] statements = importList.getImportStatements();
          if (statements.length > 0) {
            int offset = statements[0].getTextRange().getStartOffset();

            String text = file.getText();
            String toInsert = "import unresolved.*;\n";
            env.logMessage("AddImportExternally: inserting " + StringUtil.escapeStringCharacters(toInsert) + " at " + file.getName() + ":" + offset);
            FileDocumentManager.getInstance().saveDocument(getDocument());

            try {
              WriteAction.run(() -> VfsUtil.saveText(getVirtualFile(), text.substring(0, offset) + toInsert + text.substring(offset)));
            }
            catch (IOException e) {
              throw new RuntimeException(e);
            }
          }
        }
      }
    }
  }
}
