import java.util.jar.JarFile;

class MyTest {
  
    Object field = Class.forName("");
    Object o = Class.forName("").getName();

    void test() {
        Runnable r = () -> new JarFile("");
        r = () -> {new JarFile("");};
        Class.forName("");
        Object j = new JarFile("");
    }
}

