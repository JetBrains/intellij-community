// "Copy 'i' to final temp variable" "true"
class ParamTypeBug {
    private static String strings[] = new String[]{ "a", "b", "c" };

    public static void main(final String ... args){
        if (args.length == 1){
            for(int i = 0; i < strings.length; i++)
                new Thread(){
                    public void run(){
                        new String(strings[<caret>i]);
                    }
                }.start();
        }
    }
}
