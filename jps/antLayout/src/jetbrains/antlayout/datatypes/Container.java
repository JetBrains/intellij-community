package jetbrains.antlayout.datatypes;

import org.apache.tools.ant.types.FileSet;
import org.apache.tools.ant.types.ZipFileSet;
import org.apache.tools.ant.BuildException;

import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;

import jetbrains.antlayout.util.TempFileFactory;
import jetbrains.antlayout.util.LayoutFileSet;

/**
 * @author max
 */
public abstract class Container extends Content {
    private List<FileSet> filesets = new ArrayList<FileSet>();
    private List<Container> children = new ArrayList<Container>();

    private String excludes = null;
    private String includes = null;

    public void addFileset(FileSet set) {
        filesets.add(set);
    }

    public void addZipfileset(ZipFileSet set) {
        filesets.add(set);
    }

    public void addDir(DirContainer container) {
        children.add(container);
    }

    public void addJar(JarContainer container) {
        children.add(container);
    }

    public void addZip(ZipContainer container) {
        children.add(container);
    }

    public void addContainer(Container container) {
        children.add(container);
    }

    public void addModule(IdeaModule module) {
        filesets.add(module);
    }

    public List<FileSet> getFilesets() {
        return filesets;
    }

    public List<Container> getChildren() {
        return children;
    }


    public void setExcludes(String excludes) {
        this.excludes = excludes;
    }

    public void setIncludes(String includes) {
        this.includes = includes;
    }

    public List<LayoutFileSet> build(TempFileFactory temp) {
        Set<LayoutFileSet> result = new HashSet<LayoutFileSet>();
        for (FileSet set : filesets) {
            result.add(createCopy(set));
        }

        for (Container child : children) {
            for (LayoutFileSet set : child.build(temp)) {
                result.add(createCopy(set));
            }
        }

        ArrayList<LayoutFileSet> list = new ArrayList<LayoutFileSet>(result);
        for (LayoutFileSet set : list) {
            if (includes != null) {
                set.setIncludes(includes);
            }

            if (excludes != null) {
                set.setExcludes(excludes);
            }
        }
        
        return list;
    }

    protected LayoutFileSet createCopy(FileSet set) {
        if (set instanceof IdeaModule) {
            return new IdeaModule((IdeaModule) set);
        }
        if (set instanceof ZipFileSet) {
            return new LayoutFileSet((ZipFileSet) set.clone());
        } else {
            return new LayoutFileSet((FileSet) set.clone());
        }
    }

    public void validateArguments() throws BuildException {
        for (Container child : children) {
            child.validateArguments();
        }
        
        for (FileSet set : filesets) {
            if (set instanceof IdeaModule) {
                ((IdeaModule) set).validateArguments();
            }
        }
    }
}
