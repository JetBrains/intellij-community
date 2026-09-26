import org.jetbrains.annotations.Nullable;

class C {
    private int[] list;

    private int find(int id) {
        Integer n = newMethod(id);
        if (n != null) return n;
        throw new RuntimeException();
    }

    private @Nullable Integer newMethod(int id) {
        for (int n : list) {
            if (n == id) {
                return n <= 0 ? 0 : n;
            }
        }
        return null;
    }
}