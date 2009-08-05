package jetbrains.antlayout.datatypes;

import jetbrains.antlayout.util.LayoutFileSet;
import org.apache.tools.ant.BuildException;

import java.io.File;

/**
 * @author max
 */
public class IdeaModule extends LayoutFileSet {
    private String name;

    public IdeaModule() {
    }

    public IdeaModule(IdeaModule fileset) {
        super(fileset);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
        setDir(getMainOutput());
    }

    private File getMainOutput() {
        String common = getProject().getProperty("modules.output");
        if (common != null) {
            return new File(new File(common), "production/" + name);
        }

        String adhoc = getProject().getProperty("module." + name +".output.main");
        return adhoc != null ? new File(adhoc) : null;
    }

    public void validateArguments() throws BuildException {
        if (name == null) {
            throw new BuildException("name attribute must be specified for moduleentry");
        }

        File main = getMainOutput();

        if (main == null || !main.exists()) {
            throw new BuildException("No production output found for module " + name +
            ". Either modules.output property references project output that doesn't contain this module or " +
                    "module." + name +".output.main is not defined or references non-existing directory.");
        }
    }
    
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        IdeaModule that = (IdeaModule) o;
        return getDir(getProject()).equals(that.getDir(getProject()));
    }

    public int hashCode() {
        return getDir(getProject()).hashCode();
    }    
}
