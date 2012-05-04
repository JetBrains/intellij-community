/**
 * Created with IntelliJ IDEA.
 * User: db
 * Date: 04.05.12
 * Time: 17:42
 * To change this template use File | Settings | File Templates.
 */
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.CLASS) public @interface Ann {
        Class[] value();
}