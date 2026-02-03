import org.jetbrains.annotations.Nullable;

class Test {
    int method() {
        Integer x = newMethod();
        if (x != null) return x;
        return 12;
    }

    @Nullable
    private Integer newMethod() {
        try {
            if(cond1) return 0;
            else if(cond2) return 1;
            System.out.println("Text");
        } finally {           
            doSomething();
        }
        return null;
    }
}