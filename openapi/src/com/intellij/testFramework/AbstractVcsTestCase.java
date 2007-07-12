package com.intellij.testFramework;

import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diff.LineTokenizer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.VcsShowConfirmationOption;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.VcsDirtyScope;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author yole
 */
public class AbstractVcsTestCase {
  protected Project myProject;
  protected VirtualFile myWorkingCopyDir;
  protected File myClientBinaryPath;
  protected IdeaProjectTestFixture myProjectFixture;

  protected RunResult runClient(String exeName, @Nullable String stdin, @Nullable final File workingDir, String[] commandLine) throws IOException {
    final List<String> arguments = new ArrayList<String>();
    arguments.add(new File(myClientBinaryPath, exeName).toString());
    Collections.addAll(arguments, commandLine);
    final ProcessBuilder builder = new ProcessBuilder().command(arguments);
    if (workingDir != null) {
      builder.directory(workingDir);
    }
    Process clientProcess = builder.start();

    final RunResult result = new RunResult();

    if (stdin != null) {
      OutputStream outputStream = clientProcess.getOutputStream();
      try {
        byte[] bytes = stdin.getBytes();
        outputStream.write(bytes);
      }
      finally {
        outputStream.close();
      }
    }

    OSProcessHandler handler = new OSProcessHandler(clientProcess, "") {
      public Charset getCharset() {
        return CharsetToolkit.getDefaultSystemCharset();
      }
    };
    handler.addProcessListener(new ProcessAdapter() {
      public void onTextAvailable(final ProcessEvent event, final Key outputType) {
        if (outputType == ProcessOutputTypes.STDOUT) {
          result.stdOut += event.getText();
        }
        else if (outputType == ProcessOutputTypes.STDERR) {
          result.stdErr += event.getText();
        }
      }
    });
    handler.startNotify();
    handler.waitFor();
    result.exitCode = clientProcess.exitValue();
    return result;
  }

  protected void initProject(final File clientRoot) throws Exception {
    final TestFixtureBuilder<IdeaProjectTestFixture> testFixtureBuilder = IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder();
    myProjectFixture = testFixtureBuilder.getFixture();
    testFixtureBuilder.addModule(JavaModuleFixtureBuilder.class).addContentRoot(clientRoot.toString());
    myProjectFixture.setUp();
    myProject = myProjectFixture.getProject();

    ((ProjectComponent) ChangeListManager.getInstance(myProject)).projectOpened();
    ((ProjectComponent) VcsDirtyScopeManager.getInstance(myProject)).projectOpened();

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        myWorkingCopyDir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(clientRoot);
        assert myWorkingCopyDir != null;
      }
    });
  }

  protected void activateVCS(final String vcsName) {
    ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(myProject);
    vcsManager.setDirectoryMapping(myWorkingCopyDir.getPath(), vcsName);
    vcsManager.updateActiveVcss();

    AbstractVcs vcs = vcsManager.findVcsByName(vcsName);
    Assert.assertEquals(1, vcsManager.getRootsUnderVcs(vcs).length);
  }

  protected VirtualFile createFileInCommand(final String name, @Nullable final String content) {
    return createFileInCommand(myWorkingCopyDir, name, content);
  }

  protected VirtualFile createFileInCommand(final VirtualFile parent, final String name, @Nullable final String content) {
    final Ref<VirtualFile> result = new Ref<VirtualFile>();
    CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
      public void run() {
        try {
          VirtualFile file = parent.createChildData(this, name);
          if (content != null) {
            file.setBinaryContent(CharsetToolkit.getUtf8Bytes(content));
          }
          result.set(file);
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    }, "", null);
    return result.get();
  }

  protected VirtualFile createDirInCommand(final VirtualFile parent, final String name) {
    final Ref<VirtualFile> result = new Ref<VirtualFile>();
    CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
      public void run() {
        try {
          VirtualFile dir = parent.createChildDirectory(this, name);
          result.set(dir);
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    }, "", null);
    return result.get();
  }

  protected void tearDownProject() throws Exception {
    if (myProject != null) {
      ((ProjectComponent) VcsDirtyScopeManager.getInstance(myProject)).projectClosed();
      ((ProjectComponent) ChangeListManager.getInstance(myProject)).projectClosed();
      ((ProjectComponent) ProjectLevelVcsManager.getInstance(myProject)).projectClosed();
      myProject = null;
    }
    if (myProjectFixture != null) {
      myProjectFixture.tearDown();
      myProjectFixture = null;
    }
  }

  protected void enableSilentOperation(final String vcsName, final VcsConfiguration.StandardConfirmation op) {
    ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(myProject);
    final AbstractVcs vcs = vcsManager.findVcsByName(vcsName);
    VcsShowConfirmationOption option = vcsManager.getStandardConfirmation(op, vcs);
    option.setValue(VcsShowConfirmationOption.Value.DO_ACTION_SILENTLY);
  }

  protected void verify(final RunResult runResult) {
    Assert.assertEquals(runResult.stdErr, 0, runResult.exitCode);
  }

  protected void verify(final RunResult runResult, final String... stdoutLines) {
    verify(runResult);
    final String[] lines = new LineTokenizer(runResult.stdOut).execute();
    Assert.assertEquals(stdoutLines.length, lines.length); 
    for(int i=0; i<stdoutLines.length; i++) {
      Assert.assertEquals(stdoutLines [i], compressWhitespace(lines [i]));
    }
  }

  private String compressWhitespace(String line) {
    while(line.indexOf("  ") > 0) {
      line = line.replace("  ", " ");
    }
    return line.trim();
  }

  protected VcsDirtyScope getAllDirtyScope() {
    VcsDirtyScopeManager dirtyScopeManager = VcsDirtyScopeManager.getInstance(myProject);
    dirtyScopeManager.markEverythingDirty();
    List<VcsDirtyScope> scopes = dirtyScopeManager.retrieveScopes();
    Assert.assertEquals(1, scopes.size());
    return scopes.get(0);
  }

  protected void renameFileInCommand(final VirtualFile fileToAdd, final String newName) {
    CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
      public void run() {
        try {
          fileToAdd.rename(this, newName);
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    }, "", null);
  }

  protected void deleteFileInCommand(final VirtualFile file) {
    CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
      public void run() {
        try {
          file.delete(this);
        }
        catch(IOException ex) {
          throw new RuntimeException(ex);
        }
      }
    }, "", this);
  }

  protected static class RunResult {
    public int exitCode = -1;
    public String stdOut = "";
    public String stdErr = "";
  }
}