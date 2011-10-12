package jetbrains.antlayout.datatypes;

import jetbrains.antlayout.util.TempFileFactory;
import jetbrains.antlayout.util.LayoutFileSet;

import java.util.ArrayList;
import java.util.List;

import org.apache.tools.ant.BuildException;

/**
 * @author max
 */
public class DirContainer extends Container {
    private String dirName;

    public void setName(String name) {
        dirName = name;
    }

    public List<LayoutFileSet> build(TempFileFactory temp) {
        List<LayoutFileSet> unprefixed = super.build(temp);
        List<LayoutFileSet> prefixed = new ArrayList<LayoutFileSet>();

        for (LayoutFileSet set : unprefixed) {
            LayoutFileSet copy = createCopy(set);
            copy.setPrefix(dirName + "/" + set.getPrefix(getProject()));
            prefixed.add(copy);
        }

        return prefixed;
    }


    public void validateArguments() throws BuildException {
        super.validateArguments();
        if (dirName == null) {
            throw new BuildException("dirname attribute must be specified for direntry");
        }
    }
}
