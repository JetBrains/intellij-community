package com.intellij.compiler;

import com.intellij.openapi.compiler.*;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.IdeaTestCase;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 *         Date: Dec 3, 2007
 */
public class CompilerOrderingTest extends IdeaTestCase {
  public void testOrdering() {
    final CompilerManager manager = CompilerManager.getInstance(myProject);
    TranslatingCompiler c1 = new MockCompiler("C1");
    TranslatingCompiler c2 = new MockCompiler("C2");
    TranslatingCompiler c3 = new MockCompiler("C3");
    TranslatingCompiler c4 = new MockCompiler("C4");
    
    manager.addTranslatingCompiler(
      c1, 
      new HashSet<FileType>(Arrays.asList(StdFileTypes.HTML)), new HashSet<FileType>(Arrays.asList(StdFileTypes.JAVA))
    );
    manager.addTranslatingCompiler(
      c2, 
      new HashSet<FileType>(Arrays.asList(StdFileTypes.JSP)), new HashSet<FileType>(Arrays.asList(StdFileTypes.HTML))
    );
    manager.addTranslatingCompiler(
      c3, 
      new HashSet<FileType>(Arrays.asList(StdFileTypes.CLASS)), new HashSet<FileType>(Arrays.asList(StdFileTypes.IDEA_PROJECT))
    );
    manager.addTranslatingCompiler(
      c4, 
      new HashSet<FileType>(Arrays.asList(StdFileTypes.ARCHIVE)), new HashSet<FileType>(Arrays.asList(StdFileTypes.HTML, StdFileTypes.JAVA))
    );
    
    
    compareDescriptions(new String[] {
      CompilerBundle.message("resource.compiler.description"),
      "groovy compiler",
      "C2",
      "C4",
      "C1", 
      CompilerBundle.message("java.compiler.description"), 
      "C3"
    }, manager.getCompilers(TranslatingCompiler.class));
  }

  private void compareDescriptions(final String[] expected, final TranslatingCompiler[] compilers) {
    List<String> actualDescriptions = new ArrayList<String>();
    for (TranslatingCompiler compiler : compilers) {
      actualDescriptions.add(compiler.getDescription());
    }
    final String[] actual = (String[])actualDescriptions.toArray(new String[actualDescriptions.size()]);

    assertEquals(expected.length, actual.length);

    for (int idx = 0; idx < expected.length; idx++) {
      assertEquals(expected[idx], actual[idx]);
    }
  }

  private static class MockCompiler implements TranslatingCompiler {
    private final String myDescription;

    private MockCompiler(String description) {
      myDescription = description;
    }

    public boolean isCompilableFile(final VirtualFile file, final CompileContext context) {
      return false;
    }

    public ExitStatus compile(final CompileContext context, final VirtualFile[] files) {
      return null;
    }

    @NotNull
    public String getDescription() {
      return myDescription;
    }

    public boolean validateConfiguration(final CompileScope scope) {
      return false;
    }
  }
}
