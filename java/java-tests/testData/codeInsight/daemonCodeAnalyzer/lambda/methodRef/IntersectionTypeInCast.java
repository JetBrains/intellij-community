import java.io.Serializable;
class Test {
    {
        Runnable r = (Runnable & Serializable)Test::new;
    }
}
