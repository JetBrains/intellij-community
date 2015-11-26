import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/** @noinspection UnusedDeclaration*/
class GenericsTest98 {
    public static void main(String[] args) throws Exception{
        List<Movable<? extends Serializable>> list = new ArrayList<Movable<? extends Serializable>> ();
        Factory factory = Factory.newInstance();
        // Doesn't compile, but Idea doesn't complain
        Mover<? extends Serializable> mover  = factory.getNew<error descr="'getNew(java.util.List<? extends Movable<T>>)' in 'Factory' cannot be applied to '(java.util.List<Movable<? extends java.io.Serializable>>)'">(list)</error>;
    }
}

abstract class Factory {
    public static Factory newInstance(){
        return null;
    }

    // This should actually be
    // public abstract <T extends Serializable> Mover<T> getNew (List<? extends Movable<? extends T>> source);
    public abstract <T extends Serializable> Mover<T> getNew (List<? extends Movable<T>> source);
}

/** @noinspection UnusedDeclaration*/
interface Movable<T extends Serializable> extends Serializable {
}

/** @noinspection UnusedDeclaration*/
interface Mover<T extends Serializable> {
}
