public class Hello {
public static void main(String... args) {
if (Math.random() > 0.5) {
System.out.println("q");
} else {
System.out.println("w");
}
main1();
}

public static void main1() {
if (Math.random() > (0.5 + .1))
System.out.println("q");
else {
System.out.println("w");
}
}
}
