import static java.lang.Integer.parseInt;

public class X {

    private String str;

    public X(String str) {
        this.str = str;
    }

    public int parseInt(){
        //The following line is rejected by the compiler but not by IntelliJ.
        return <ref>parseInt(str);
    }

    public static void main(String[] args) {
        X x=new X("123");
        int i=x.parseInt();
        System.out.println(i);
    }
}