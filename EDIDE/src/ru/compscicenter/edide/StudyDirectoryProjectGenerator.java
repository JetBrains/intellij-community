package ru.compscicenter.edide;

import com.intellij.facet.ui.ValidationResult;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.DirectoryProjectGenerator;
import com.intellij.remotesdk.RemoteSdkData;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User: lia
 */
public class StudyDirectoryProjectGenerator implements DirectoryProjectGenerator {

    @Nls
    @NotNull
    @Override
    public String getName() {
        return "Study project";
    }

    @Nullable
    @Override
    public Object showGenerationSettings(VirtualFile virtualFile) throws ProcessCanceledException {
        return null;
    }

    @Override
    public void generateProject(@NotNull Project project, @NotNull VirtualFile virtualFile, @Nullable Object o, @NotNull Module module) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @NotNull
    @Override
    public ValidationResult validate(@NotNull String s) {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }
}
