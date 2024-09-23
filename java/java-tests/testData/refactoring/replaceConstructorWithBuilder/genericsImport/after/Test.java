import java.io.Serializable;

public class Test<T extends Serializable> {
    private final String name;
    private final Class<T> clazz;

    public Test(String name, Class<T> clazz) {
        this.name = name;
        this.clazz = clazz;
    }

  public static final Test<Boolean> BOOLEAN = new Builder().setName("b").setClazz(Boolean.class).createTest();
}