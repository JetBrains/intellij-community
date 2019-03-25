// "Transform body to single exit-point form" "true"
class Test {
    native String get(String s);

    String <caret>test(String[] data) {
        if (data == null) {
            return get("foo");
        }
        String s = data[0];
        int i=0;
        if (data.length > 2) {
            if (data[2] != null) {
                if(data[2].isEmpty()) {
                    return null;
                }
            }
            while (true) {
                if (!s.isEmpty()) {
                    if (s.length() > 2) {
                        return s;
                    }
                }
                System.out.println(s);
                s = data[i++];
            }
        }
        return null;
    }
}