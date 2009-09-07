package com.intellij.compiler.artifacts;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ArtifactManager;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.elements.PackagingElement;
import com.intellij.packaging.impl.elements.ArchivePackagingElement;
import com.intellij.packaging.impl.elements.DirectoryPackagingElement;
import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author nik
 */
public class ArtifactsTestUtil {
  public static String printToString(PackagingElement element, int level) {
    StringBuilder builder = new StringBuilder(StringUtil.repeatSymbol(' ', level));
    if (element instanceof ArchivePackagingElement) {
      builder.append(((ArchivePackagingElement)element).getArchiveFileName());
    }
    else if (element instanceof DirectoryPackagingElement) {
      builder.append(((DirectoryPackagingElement)element).getDirectoryName()).append("/");
    }
    else {
      builder.append(element.toString());
    }
    builder.append("\n");
    if (element instanceof CompositePackagingElement) {
      for (PackagingElement<?> child : ((CompositePackagingElement<?>)element).getChildren()) {
        builder.append(printToString(child, level + 1));
      }
    }
    return builder.toString();
  }

  public static void assertLayout(PackagingElement element, String expected) {
    assertEquals(adjustMultiLine(expected), printToString(element, 0));
  }

  private static String adjustMultiLine(String expected) {
    final List<String> strings = StringUtil.split(StringUtil.trimStart(expected, "\n"), "\n");
    int min = Integer.MAX_VALUE;
    for (String s : strings) {
      int k = 0;
      while (k < s.length() && s.charAt(k) == ' ') {
        k++;
      }
      min = Math.min(min, k);
    }
    List<String> lines = new ArrayList<String>();
    for (String s : strings) {
      lines.add(s.substring(min));
    }
    return StringUtil.join(lines, "\n") + "\n";      
  }

  public static void assertLayout(Project project, String artifactName, String expected) {
    assertLayout(findArtifact(project, artifactName).getRootElement(), expected);
  }

  public static void assertOutputPath(Project project, String artifactName, String expected) {
    assertEquals(expected, findArtifact(project, artifactName).getOutputPath());
  }

  public static void assertOutputFileName(Project project, String artifactName, String expected) {
    assertEquals(expected, findArtifact(project, artifactName).getRootElement().getName());
  }

  private static Artifact findArtifact(Project project, String artifactName) {
    final ArtifactManager manager = ArtifactManager.getInstance(project);
    final Artifact artifact = manager.findArtifact(artifactName);
    assertNotNull("'" + artifactName + "' artifact not found", artifact);
    return artifact;
  }
}
