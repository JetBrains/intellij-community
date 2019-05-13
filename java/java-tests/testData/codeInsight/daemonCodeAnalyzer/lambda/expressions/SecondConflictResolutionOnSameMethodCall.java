import java.util.Arrays;
import java.util.function.Function;
import java.util.stream.Stream;

class CommandTest {

    public static class Command {
        public String[] getKeywords() { return new String[] {"GET", "PUT", "POST"}; }
        public String getDescription() { return "Some HTTP command"; }
    }


    public static void main(Stream<Command> stream) {
        stream.map(cmd -> Arrays.stream(cmd.getKeywords()).map(key -> String.format("%s -> %s", key, cmd.getDescription()))).flatMap(Function.identity());
    }
}