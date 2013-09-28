import java.util.*;
import java.io.File;
class Foo {
    public Collection<BuildTarget<?>> getDependencies(LayoutElementBuilderService builder, JpsPackagingElement element, TargetOutputIndex outputIndex) {
        return builder.getDependencies(element, outputIndex);
    }
}

class BuildTarget<R extends BuildRootDescriptor> {}

interface TargetOutputIndex {
    Collection<BuildTarget<?>> getTargetsByOutputFile(File file);
}

class BuildRootDescriptor {}

class LayoutElementBuilderService<E extends JpsPackagingElement> {
    public Collection<? extends BuildTarget<?>> getDependencies(E element, TargetOutputIndex outputIndex) {
        return Collections.emptyList();
    }
}

class JpsPackagingElement {}
