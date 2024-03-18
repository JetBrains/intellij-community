import org.jetbrains.annotations.Nullable;

class Test {
    private String test(int[] all) {
        for (int z : all) {
            String sample = newMethod(z);
            if (sample != null) return sample;
        }
        return "default";
    }

    private @Nullable String newMethod(int z) {
        if (z > 5) return null;
        if (z < 0) return "sample";
        return null;
    }
}