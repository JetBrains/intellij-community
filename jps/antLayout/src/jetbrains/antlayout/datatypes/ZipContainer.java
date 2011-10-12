package jetbrains.antlayout.datatypes;

import jetbrains.antlayout.util.TempFileFactory;
import jetbrains.antlayout.util.LayoutFileSet;
import org.apache.tools.ant.taskdefs.Zip;
import org.apache.tools.ant.BuildException;

import java.io.File;
import java.util.Arrays;
import java.util.List;

/**
 * @author max
 */
public class ZipContainer extends Container {
    private String name;
    protected Zip task;

    public ZipContainer() {
        task = createTask();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<LayoutFileSet> build(TempFileFactory temp) {
        List<LayoutFileSet> built = super.build(temp);
        File dest = temp.allocateTempFile(name);

        task.setProject(getProject());
        task.setDestFile(dest);

        for (LayoutFileSet set : built) {
            task.addZipfileset(set);
        }

        LayoutFileSet result = new LayoutFileSet();
        result.setFile(dest);

        task.perform();

        return Arrays.asList(result);
    }

    protected Zip createTask() {
        Zip task = new Zip();
        task.setTaskName("zip");
        return task;
    }

    public void setCompress(boolean compress) {
        task.setCompress(compress);
    }

    public void setFilesonly(boolean f) {
        task.setFilesonly(f);
    }

    public void setDuplicate(Zip.Duplicate df) {
        task.setDuplicate(df);
    }

    public void setEncoding(String encoding) {
        task.setEncoding(encoding);
    }

    public void setBasedir(File baseDir) {
        task.setBasedir(baseDir);
    }

    public void validateArguments() throws BuildException {
        super.validateArguments();

        if (name == null) {
            throw new BuildException("name attribute must be specified for zipentry or jarentry");
        }
    }
}
