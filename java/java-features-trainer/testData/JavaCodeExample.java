public class Main {

    private static final Integer field = 322;

    public static void main(String[] args) {
        StaticClass staticClass = new StaticClass();
        int abc = 3;
        <caret>int bcd = 5 + field;
        function(true);
        if(abc < bcd && function(true)) {
            System.out.println(abc * bcd - field);
        }
        staticClass.main(null && field > 0);
    }

    private static boolean function(boolean arg) {
        return arg;
    }

    private static class StaticClass {
        public static void main(String[] args) {
            function(false);
        }

        private static boolean function(boolean arg) {
            return arg && function(function && function || arg);
        }

        private static boolean function = true;

        public int cyclesFunction(int ere) {
            int i = 10;
            for(int j = 0; j < i; j++) {
                int res = j - j + j;
                System.out.println(res - j);
            }
            i += 10;
            while(i > 0) {
                System.out.println(i * i);
                i--;
            }
            if  (true) { i++; }
            return i;
        }

        public String stringFunction(String sss) {
            StringBuilder builder = new StringBuilder();
            builder.append("433" + "322" + 'e' + "wrwer");
            String str = builder.toString() + '2' + "123";
            return builder.toString() + "322" + "erer" +
                    "true" + "or false" + str;
        }

        public StaticClass selfReturningFunction(int arg) {
            return this;
        }

        public String nonStaticField = "123123123";

        static {
            StaticClass instance = new StaticClass();
            String ss = instance.selfReturningFunction(322).selfReturningFunction(instance.cyclesFunction(0))
                    .selfReturningFunction(1).nonStaticField;
        }
    }
}
