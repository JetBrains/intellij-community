import java.util.List;

class Some {

    public static void main(String[] args) {
        boolean x = true, y = true, z = true, t = true;
        boolean r = <warning descr="Condition 'x ^ y ^ z ^ t' is always 'false'">x ^ y ^ z ^ t</warning>;
        System.out.println("r: " + r);
    }

}
