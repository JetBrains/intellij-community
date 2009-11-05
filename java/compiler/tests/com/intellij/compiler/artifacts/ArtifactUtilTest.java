package com.intellij.compiler.artifacts;

import com.intellij.packaging.impl.artifacts.ArtifactUtil;
import com.intellij.packaging.impl.artifacts.ParentElementProcessor;
import com.intellij.openapi.util.Pair;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.impl.artifacts.PlainArtifactType;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author nik
 */
public class ArtifactUtilTest extends PackagingElementsTestCase {

  public void testProcessParents() throws Exception {
    final Artifact exploded = addArtifact("exploded:", root().build());
    final Artifact war = addArtifact("war", PlainArtifactType.getInstance(),
                                    archive("web.war")
                                        .dir("dir")
                                           .artifact(exploded)
                                        .build());
    addArtifact("ear", PlainArtifactType.getInstance(),
                archive("ear.ear")
                    .artifact(war)
                    .build());
    final MyParentElementProcessor processor = new MyParentElementProcessor();

    ArtifactUtil.processParents(exploded, getContext(), processor, 2);
    assertEquals("war:dir;" +
                 "war:web.war/dir;" +
                 "ear:ear.ear/web.war/dir;", processor.getLog());

    ArtifactUtil.processParents(exploded, getContext(), processor, 1);
    assertEquals("war:dir;" +
                 "war:web.war/dir;", processor.getLog());

    ArtifactUtil.processParents(exploded, getContext(), processor, 0);
    assertEquals("war:dir;", processor.getLog());

    ArtifactUtil.processParents(war, getContext(), processor, 2);
    assertEquals("war:web.war;ear:ear.ear/web.war;", processor.getLog());

  }

  private class MyParentElementProcessor extends ParentElementProcessor {
    private StringBuilder myLog = new StringBuilder();

    @Override
    public boolean process(@NotNull CompositePackagingElement<?> element, @NotNull List<Pair<Artifact,CompositePackagingElement<?>>> parents, @NotNull Artifact artifact) {
      myLog.append(artifact.getName()).append(":").append(element.getName());
      for (Pair<Artifact, CompositePackagingElement<?>> parent : parents) {
        myLog.append("/").append(parent.getSecond().getName());
      }
      myLog.append(";");
      return true;
    }

    public String getLog() {
      final String output = myLog.toString();
      myLog.setLength(0);
      return output;
    }
  }
}
