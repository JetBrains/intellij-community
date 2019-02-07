// "Unwrap code block of labeled rule" "LIKE_UNUSED_SYMBOL"
class C {
    String foo(int n) {
        switch (n) {
            case 1 -> /*1*/<caret>{/*2*/throw /*3*/new RuntimeException(/*4*/)/*5*/; /*6*/}/*7*/
            default ->System.out.println();
        };
    }
}