import java.util.Arrays;
import java.util.List;

class X {
    {
        @SuppressWarnings("unchecked")
        List<Constructor<Test>> constructors = (List) Arrays.asList(getClass().getConstructors());
    }

}
