package jetbrains.antlayout.datatypes;

import jetbrains.antlayout.util.LayoutFileSet;
import jetbrains.antlayout.util.TempFileFactory;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.types.FileSet;
import org.apache.tools.ant.types.ZipFileSet;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * @author max
 */
public abstract class Container extends Content {
    private List<Content> children = new ArrayList<Content>();

    private String excludes = null;
    private String includes = null;

    public void addFileset(FileSet set) {
        children.add(new FileSetContainer(set));
    }

    public void addZipfileset(ZipFileSet set) {
        children.add(new FileSetContainer(set));
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

    public void addRenamedFile(RenamedFileContainer container) {
        children.add(container);
    }

    public void addExtractedDir(ExtractedDirContent content) {
        children.add(content);
    }

    public void addContainer(Container container) {
        children.add(container);
    }

    public void addContent(Content content) {
        children.add(content);
    }

    public void addModule(IdeaModule module) {
        children.add(new FileSetContainer(module));
    }

    public List<Content> getChildren() {
        return children;
    }


    public void setExcludes(String excludes) {
        this.excludes = excludes;
    }

    public void setIncludes(String includes) {
        this.includes = includes;
    }

    public List<LayoutFileSet> build(TempFileFactory temp) {
        Set<LayoutFileSet> result = new LinkedHashSet<LayoutFileSet>();

        for (Content child : children) {
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

    protected static LayoutFileSet createCopy(FileSet set) {
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
        for (Content child : children) {
            child.validateArguments();
        }
    }
}
