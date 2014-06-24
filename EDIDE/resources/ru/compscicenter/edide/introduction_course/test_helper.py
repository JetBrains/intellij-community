def get_file_text(path):
    # TODO: get file text by path
    return ""

def get_file_output(path):
    # TODO: get file output by path
    return ""

def test_file_importable(path):
    # TODO: test there is no obvious syntax errors
    return ""

def import_file(path):
    #TODO: return imported file
    return ""

def test_is_empty(path):
    file_text = get_file_text(path)

    if len(file_text) > 0:
        return "Bravo"
    return "The file is empty. Please, reload the task and try again."

def test_is_initial_text(path, text, error_text):
    file_text = get_file_text(path)
    if file_text.strip() == text:
        return error_text
    return "Bravo"

def test_window_text_deleted(path, text, error_text):
    file_text = get_file_text(path)
    if file_text.strip() == text:
        return error_text
    return "Bravo"

def run_common_tests(text, text_with_deleted_windows, error_text):
    # TODO: get filepath. Let's now assume that we pass it as the last item in command-line
    import sys
    path = sys.argv[-1]
    test_file_importable(path)
    test_is_empty(path)
    test_is_initial_text(path, text, error_text)
    test_window_text_deleted(path, text_with_deleted_windows, error_text)