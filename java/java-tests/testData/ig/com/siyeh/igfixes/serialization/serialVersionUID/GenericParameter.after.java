import java.io.Serializable;

public class GenericParameter implements Serializable {

    private static final long serialVersionUID = 8306950084505752582L;

    <T extends String> void s(T t) {}
}