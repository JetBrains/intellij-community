public class Test { 
    public static void main() {
	class Inner1{};
	class <warning>Inner2</warning> {};
	new Inner1();
    }
}
