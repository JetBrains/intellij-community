package com.intellij.codeInsight.daemon;

import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.intention.IntentionManager;
import com.intellij.concurrency.JobSchedulerImpl;
import com.intellij.lang.LanguageAnnotators;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.lang.injection.MultiHostInjector;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.impl.source.tree.injected.JavaConcatenationInjectorManager;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import com.intellij.util.Function;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class LightAdvHighlightingPerformanceTest extends LightDaemonAnalyzerTestCase {
  Disposable my = Disposer.newDisposable();
  @Override
  protected void setUp() throws Exception {
    super.setUp();

    Disposer.register(my, BlockExtensions.create(Extensions.getRootArea().getExtensionPoint(LanguageAnnotators.EP_NAME)));
    Disposer.register(my, BlockExtensions.create(Extensions.getRootArea().getExtensionPoint(LineMarkerProviders.EP_NAME)));
    Disposer.register(my, BlockExtensions.create(Extensions.getArea(getProject()).getExtensionPoint(JavaConcatenationInjectorManager.CONCATENATION_INJECTOR_EP_NAME)));
    Disposer.register(my, BlockExtensions.create(Extensions.getArea(getProject()).getExtensionPoint(MultiHostInjector.MULTIHOST_INJECTOR_EP_NAME)));

    IntentionManager.getInstance().getAvailableIntentionActions();  // hack to avoid slowdowns in PyExtensionFactory
    PathManagerEx.getTestDataPath(); // to cache stuff
  }

  @Override
  protected void tearDown() throws Exception {
    Disposer.dispose(my);
    super.tearDown();
  }

  private static class BlockExtensions<T> implements Disposable {
    private final ExtensionPoint<T> myEp;
    private T[] myExtensions;
    public BlockExtensions(ExtensionPoint<T> extensionPoint) {
      myEp = extensionPoint;
      block();
    }
    void block() {
      myExtensions = myEp.getExtensions();
      for (T extension : myExtensions) {
        myEp.unregisterExtension(extension);
      }
    }

    void unblock() {
      for (T extension : myExtensions) {
        myEp.registerExtension(extension);
      }
      myExtensions = null;
    }
    public void dispose() {
      unblock();
    }
    public static <T> BlockExtensions<T> create(ExtensionPoint<T> extensionPoint) {
      return new BlockExtensions<T>(extensionPoint);
    }
  }

  private List<HighlightInfo> doTest(final long maxMillis) throws Exception {
    @NonNls String filePath = LightAdvHighlightingTest.BASE_PATH + "/" + getTestName(false) + ".java";
    configureByFile(filePath);

    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    getFile().getText(); //to load text
    CodeInsightTestFixtureImpl.ensureIndexesUpToDate(getProject());

    final List<HighlightInfo> infos = new ArrayList<HighlightInfo>();
    PlatformTestUtil.assertTiming("Performance failed", maxMillis, new Runnable() {
      public void run() {
        infos.clear();
        DaemonCodeAnalyzerImpl.getInstance(getProject()).restart();
        List<HighlightInfo> h = doHighlighting();
        infos.addAll(h);
      }
    });
    return DaemonAnalyzerTestCase.filter(infos, HighlightSeverity.ERROR);
  }

  public void testaThinlet() throws Exception {
    List<HighlightInfo> errors = doTest(24000 - JobSchedulerImpl.CORES_COUNT * 1000);
    dump("thinlet", errors);
    assertEquals(1157, errors.size());
  }

  private static void dump(String msg, List<HighlightInfo> errors) {
    if (true) return;
    Collections.sort(errors, new Comparator<HighlightInfo>() {
      @Override
      public int compare(HighlightInfo o1, HighlightInfo o2) {
        return o1.toString().compareTo(o2.toString());
      }
    });
    final String s = StringUtil.join(errors, StringUtil.createToStringFunction(HighlightInfo.class), "\n");
    System.out.println(msg+":\n-----------------------------------------");
    System.out.println(s);
    System.out.println("\n--------------------------------------------");
  }

  public void testaClassLoader() throws Exception {
    List<HighlightInfo> errors = doTest(10000 - JobSchedulerImpl.CORES_COUNT * 1000);
    dump("classloader", errors);
    assertEquals(176, errors.size());
  }
}
