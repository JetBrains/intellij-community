def get_file_text(path):
    """ get file text by path"""
    file_io = open(path)
    text = file_io.read()
    file_io.close()
    return text

def get_file_output(path):
    # TODO: get file output by path
    return ""

def test_file_importable(path):
    """ tests there is no obvious syntax errors"""
    try:
        import_file(path)
    except ImportError:
        return "#study_plugin File contains syntax errors"

    return "#study_plugin test OK"

def import_file(path):
    """ returns imported file """
    import imp
    tmp = imp.load_source('tmp', path)
    return tmp

def test_is_empty(path):
    file_text = get_file_text(path)

    if len(file_text) > 0:
        return "#study_plugin test OK"
    return "#study_plugin The file is empty. Please, reload the task and try again."

def test_is_initial_text(path, text, error_text):
    file_text = get_file_text(path)

    if file_text.strip() == text:
        return "#study_plugin " + error_text
    return "#study_plugin test OK"

def test_window_text_deleted(path, text, error_text):
    file_text = get_file_text(path)
    if file_text.strip() == text:
        return "#study_plugin " + error_text
    return "#study_plugin test OK"

def run_common_tests(text, text_with_deleted_windows, error_text):
    # TODO: get filepath. Let's now assume that we pass it as the last item in command-line
    import sys
    path = sys.argv[-1]

    print(test_file_importable(path))
    print(test_is_empty(path))
    print(test_is_initial_text(path, text, error_text))
    print(test_window_text_deleted(path, text_with_deleted_windows, error_text))