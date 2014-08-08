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
        failed("File contains syntax errors")
        return
    except SyntaxError:
        failed("File contains syntax errors")
        return
    except NameError:
        failed("File contains syntax errors")
        return

    passed()

def import_file(path):
    """ returns imported file """
    import imp
    tmp = imp.load_source('tmp', path)
    return tmp

def test_is_not_empty(path):
    file_text = get_file_text(path)

    if len(file_text) > 0:
        passed()
    else:
        failed("The file is empty. Please, reload the task and try again.")

def test_is_initial_text(path, text, error_text):
    file_text = get_file_text(path)

    if file_text.strip() == text:
        failed(error_text)
    else:
        passed()

def test_text_equals(text, error_text):
    import sys
    path = sys.argv[-1]
    file_text = get_file_text(path)

    if file_text.strip() == text:
        passed()
    else:
        failed(error_text)

def test_window_text_deleted(path, text, error_text):
    file_text = get_file_text(path)
    if file_text.strip() == text:
        failed(error_text)
    else:
        passed()

def failed(message="Please, reload the task and try again."):
    print("#study_plugin FAILED + " + message)

def passed():
    print("#study_plugin test OK")

# TODO: check text in exact task window

def run_common_tests(text, text_with_deleted_windows, error_text):

    import sys
    path = sys.argv[-1]

    test_file_importable(path)
    test_is_not_empty(path)
    test_is_initial_text(path, text, error_text)
    test_window_text_deleted(path, text_with_deleted_windows, error_text)