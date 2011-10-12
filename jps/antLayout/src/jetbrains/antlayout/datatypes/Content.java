package jetbrains.antlayout.datatypes;

import org.apache.tools.ant.types.DataType;
import org.apache.tools.ant.BuildException;

import java.util.List;

import jetbrains.antlayout.util.TempFileFactory;
import jetbrains.antlayout.util.LayoutFileSet;

/**
 * @author max
 */
public abstract class Content extends DataType {
    public abstract List<LayoutFileSet> build(TempFileFactory temp);

    public abstract void validateArguments() throws BuildException;
}
