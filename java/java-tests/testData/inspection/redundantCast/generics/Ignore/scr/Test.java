import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class CastPreventsNPEDetection {
    @Nullable Object getParent() {
        return null;
    }

    void f() {
        ((ChildCastImpl)this).getParent().toString();
        ((CastPreventsNPEDetection)this).getParent().toString();
    }
}

class ChildCastImpl extends CastPreventsNPEDetection {
    @NotNull
    @Override
    Object getParent() {
        return super.getParent();    //To change body of overridden methods use File | Settings | File Templates.
    }
}