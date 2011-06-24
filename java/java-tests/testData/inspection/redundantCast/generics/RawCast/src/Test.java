import java.util.Map;
import java.util.Properties;

public class Main {
    public static void main(String[] args) throws Exception {
        Properties properties = new Properties();

        Map<String, String> map = (Map) properties;
        System.out.println(map);
    }
}
