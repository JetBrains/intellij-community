// "Unwrap code block of labeled rule" "GENERIC_ERROR_OR_WARNING"
class C {
    String foo(int n) {
        switch (n) {
            case 1 -> /*1*//*2*/throw /*3*/new RuntimeException(/*4*/)/*5*/; /*6*//*7*/
            default ->System.out.println();
        };
    }
}