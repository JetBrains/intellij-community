package Fabric;
public class Fabric extends AFabric {
    public static Fabric create() {
        return new Fabric();
    }

    public static void main(String[] args) {
        Fabric f = Fabric.create();

    }
}