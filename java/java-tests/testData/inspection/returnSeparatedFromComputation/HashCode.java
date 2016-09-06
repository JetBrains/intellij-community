class T {
    int size;
    int width;
    int height;

    public int hashCode() {
        int result = size;
        result = 31 * result + width;
        result = 31 * result + height;
        return result;
    }
}