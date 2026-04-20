abstract class MarkdownFirstClassParam<T> {
    /// <caret>
    /// @param param1
    /// @param param2
    /// @return
    /// @throws RuntimeException
    abstract String methodAbstract(String param1, String param2) throws RuntimeException;
}