package ru.compscicenter.edide;

import com.intellij.facet.ui.ValidationResult;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Log;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.DirectoryProjectGenerator;
import com.intellij.remotesdk.RemoteSdkData;
import com.jetbrains.python.newProject.PyNewProjectSettings;
import com.jetbrains.python.remote.PythonRemoteInterpreterManager;
import com.jetbrains.python.remote.RemoteProjectSettings;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;

/**
 * User: lia
 */
public class StudyDirectoryProjectGenerator implements DirectoryProjectGenerator {

    @Nls
    @NotNull
    @Override
    public String getName() {
        return "My Study project";
    }

    @Nullable
    @Override
    public Object showGenerationSettings(VirtualFile baseDir) throws ProcessCanceledException {
        PyNewProjectSettings settings = new PyNewProjectSettings();
        if (PythonSdkType.isRemote(settings.getSdk())) {
            PythonRemoteInterpreterManager manager = PythonRemoteInterpreterManager.getInstance();
            assert manager != null;
            return manager.showRemoteProjectSettingsDialog(baseDir, (RemoteSdkData) settings.getSdk().getSdkAdditionalData());
        } else {
            return null;
        }
    }

    @Override
    public void generateProject(@NotNull Project project, @NotNull final VirtualFile baseDir,
                                @Nullable Object settings, @NotNull Module module) {
        PyNewProjectSettings pySettings = new PyNewProjectSettings();
        if (settings instanceof RemoteProjectSettings) {
            PythonRemoteInterpreterManager manager = PythonRemoteInterpreterManager.getInstance();
            assert manager != null;
            manager.createDeployment(project, baseDir, (RemoteProjectSettings) settings,
                    (RemoteSdkData) pySettings.getSdk().getSdkAdditionalData());
        }
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
            @Override
            public void run() {
                try {
                    final VirtualFile vf = baseDir.createChildData(this, "helloworld.py");
                    vf.setWritable(true);
                    InputStream ip = StudyDirectoryProjectGenerator.class.getResourceAsStream("helloworld.py");
                    BufferedReader bf = new BufferedReader(new InputStreamReader(ip));
                    OutputStream os = vf.getOutputStream(this);
                    while (bf.ready()) {
                        os.write(bf.readLine().getBytes());
                    }
                    ip.close();
                    os.close();
                } catch (IOException e) {
                    Log.print("Problems with creating file");
                    Log.print(e.toString());
                    Log.flush();
                }

            }
        });
    }

    @NotNull
    @Override
    public ValidationResult validate(@NotNull String s) {
        PyNewProjectSettings pySettings = new PyNewProjectSettings();
        if (PythonSdkType.isRemote(pySettings.getSdk())) {
            if (PythonRemoteInterpreterManager.getInstance() == null) {
                return new ValidationResult(PythonRemoteInterpreterManager.WEB_DEPLOYMENT_PLUGIN_IS_DISABLED);
            }
        }
        return ValidationResult.OK;
    }
}
