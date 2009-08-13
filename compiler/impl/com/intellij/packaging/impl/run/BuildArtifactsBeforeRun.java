package com.intellij.packaging.impl.run;

import com.intellij.execution.BeforeRunTaskProvider;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.compiler.*;
import com.intellij.openapi.compiler.Compiler;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ArtifactManager;
import com.intellij.packaging.artifacts.ArtifactPointer;
import com.intellij.packaging.artifacts.ArtifactPointerManager;
import com.intellij.packaging.impl.compiler.ArtifactCompileScope;
import com.intellij.packaging.impl.compiler.IncrementalArtifactsCompiler;
import com.intellij.packaging.impl.compiler.ArtifactAwareCompiler;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * @author nik
 */
public class BuildArtifactsBeforeRun implements BeforeRunTaskProvider<BuildArtifactsBeforeRunTask> {
  public static final Key<BuildArtifactsBeforeRunTask> ID = Key.create("BuildArtifacts");
  private Project myProject;

  public BuildArtifactsBeforeRun(Project project) {
    myProject = project;
  }

  public Key<BuildArtifactsBeforeRunTask> getId() {
    return ID;
  }

  public String getDescription(RunConfiguration runConfiguration, BuildArtifactsBeforeRunTask task) {
    final List<ArtifactPointer> pointers = task.getArtifactPointers();
    if (!task.isEnabled() || pointers.isEmpty()) {
      return "Build Artifacts";
    }
    if (pointers.size() == 1) {
      return "Build '" + pointers.get(0).getName() + "' artifact";
    }
    return "Build " + pointers.size() + " artifacts";
  }

  public boolean hasConfigurationButton() {
    return true;
  }

  public BuildArtifactsBeforeRunTask createTask(RunConfiguration runConfiguration) {
    if (!ApplicationManagerEx.getApplicationEx().isInternal()) return null;
    return new BuildArtifactsBeforeRunTask(myProject);
  }

  public void configureTask(RunConfiguration runConfiguration, BuildArtifactsBeforeRunTask task) {
    final Artifact[] artifacts = ArtifactManager.getInstance(myProject).getArtifacts();
    Set<ArtifactPointer> pointers = new THashSet<ArtifactPointer>();
    for (Artifact artifact : artifacts) {
      pointers.add(ArtifactPointerManager.getInstance(myProject).create(artifact));
    }
    pointers.addAll(task.getArtifactPointers());
    ArtifactChooser chooser = new ArtifactChooser(new ArrayList<ArtifactPointer>(pointers));
    chooser.markElements(task.getArtifactPointers());

    DialogBuilder builder = new DialogBuilder(myProject);
    builder.setTitle("Select Artifacts");
    builder.addOkAction();
    builder.addCancelAction();
    builder.setCenterPanel(chooser);
    builder.setPreferedFocusComponent(chooser);
    if (builder.show() == DialogWrapper.OK_EXIT_CODE) {
      task.setArtifactPointers(chooser.getMarkedElements());
    }
  }

  public boolean executeTask(DataContext context, RunConfiguration configuration, final BuildArtifactsBeforeRunTask task) {
    final Ref<Boolean> result = Ref.create(false);
    final Semaphore finished = new Semaphore();

    final List<Artifact> artifacts = new ArrayList<Artifact>();
    new ReadAction() {
      protected void run(final Result result) {
        for (ArtifactPointer pointer : task.getArtifactPointers()) {
          ContainerUtil.addIfNotNull(pointer.getArtifact(), artifacts);
        }
      }
    }.execute();
    
    final CompileStatusNotification callback = new CompileStatusNotification() {
      public void finished(boolean aborted, int errors, int warnings, CompileContext compileContext) {
        result.set(!aborted && errors == 0);
        finished.up();
      }
    };
    final CompilerFilter compilerFilter = new CompilerFilter() {
      public boolean acceptCompiler(Compiler compiler) {
        return compiler instanceof IncrementalArtifactsCompiler
               || compiler instanceof ArtifactAwareCompiler && ((ArtifactAwareCompiler)compiler).shouldRun(artifacts);
      }
    };

    ApplicationManager.getApplication().invokeAndWait(new Runnable() {
      public void run() {
        final CompilerManager manager = CompilerManager.getInstance(myProject);
        finished.down();
        manager.make(ArtifactCompileScope.create(myProject, artifacts), compilerFilter, callback);
      }
    }, ModalityState.NON_MODAL);

    finished.waitFor();
    return result.get();
  }
}
