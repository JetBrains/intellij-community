import static java.lang.annotation.ElementType.*;

class C {
    {
        B b = val<caret>
        // no Enum.valueOf() should be inserted
    }
}

interface B {}
