import sys

def get_file_text(path):
    """ get file text by path"""
    file_io = open(path, "r")
    text = file_io.read()
    file_io.close()
    return text

def get_file_output(path):
    # TODO: get file output by path
    return ""

def test_file_importable():
    """ tests there is no obvious syntax errors"""
    path = sys.argv[-1]
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

def import_task_file():
    """ returns imported file """
    path = sys.argv[-1]
    return import_file(path)

def test_is_not_empty():
    path = sys.argv[-1]
    file_text = get_file_text(path)

    if len(file_text) > 0:
        passed()
    else:
        failed("The file is empty. Please, reload the task and try again.")

def test_is_initial_text(error_text="You should modify the file"):
    path = sys.argv[-1]
    text = get_initial_text(path)
    file_text = get_file_text(path)

    if file_text.strip() == text:
        failed(error_text)
    else:
        passed()

def get_initial_text(path):
    course_lib = sys.argv[-2]

    import os
    # path format is "project_root/lessonX/taskY/file.py"
    task_index = path.rfind(os.sep, 0, path.rfind(os.sep))
    index = path.rfind(os.sep, 0, task_index)
    relative_path = path[index+1:]
    initial_file_path = os.path.join(course_lib, relative_path)
    return get_file_text(initial_file_path)


def test_text_equals(text, error_text):
    path = sys.argv[-1]
    file_text = get_file_text(path)

    if file_text.strip() == text:
        passed()
    else:
        failed(error_text)

def test_window_text_deleted(error_text="Don't just delete task text"):
    windows = get_task_windows()

    for window in windows:
        if len(window) == 0:
            failed(error_text)
            return
    passed()


def failed(message="Please, reload the task and try again."):
    print("#study_plugin FAILED + " + message)

def passed():
    print("#study_plugin test OK")

def get_task_windows():
    prefix = "#study_plugin_window = "
    path = sys.argv[-1]
    import os
    windows_path = os.path.splitext(path)[0] + "_windows"
    windows = []
    f = open(windows_path, "r")
    window_text = ""
    first = True
    for line in f.readlines():
        if line.startswith(prefix):
            if not first:
                windows.append(window_text.strip())
            else:
                first = False
            window_text = line[len(prefix):]
        else:
            window_text += line

    if window_text:
        windows.append(window_text.strip())

    f.close()
    return windows

def run_common_tests(error_text="Please, reload file and try again"):
    test_file_importable()
    test_is_not_empty()
    test_is_initial_text(error_text)
    test_window_text_deleted(error_text)