// "Replace lambda with method reference" "true"
import java.util.function.Function;
class Bar {
    Function<Class<?> , String>  s = (c) -> c.getCanonica<caret>lName();
}