// package-private class in the middle

package x;
class ComponentClass {
    public int size;
}
public class ContainerClass {
    public ComponentClass cc = new ComponentClass();
}
