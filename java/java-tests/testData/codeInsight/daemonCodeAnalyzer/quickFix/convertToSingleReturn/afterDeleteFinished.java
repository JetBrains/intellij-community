// "Transform body to single exit-point form" "true-preview"
class Test {
    native String get(String s);

    String test(String[] data) {
        String result = null;
        boolean finished = false;
        if (data == null) {
            result = get("foo");
        } else {
            String s = data[0];
            int i=0;
            if (data.length > 2) {
                if (data[2] != null) {
                    if(data[2].isEmpty()) {
                        finished = true;
                    }
                }
                if (!finished) {
                    while (true) {
                        if (!s.isEmpty()) {
                            if (s.length() > 2) {
                                result = s;
                                break;
                            }
                        }
                        System.out.println(s);
                        s = data[i++];
                    }
                }
            }
        }
        return result;
    }
}