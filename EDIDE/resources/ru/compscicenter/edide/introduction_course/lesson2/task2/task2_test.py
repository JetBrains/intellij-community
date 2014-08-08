from test_helper import test_is_not_empty, test_is_initial_text, test_window_text_deleted, import_file, passed, failed


def test_is_identifier(path):
    try:
        import_file(path)
    except NameError:
        passed()
    except SyntaxError:
        failed("Used invalid identifier")
    failed("Use undefined variable")


if __name__ == '__main__':
    import sys
    path = sys.argv[-1]
    test_is_not_empty(path)
    text = '''variable = 1
print (other variable)'''
    text_with_deleted_windows = '''variable = 1
print ()'''
    error_text = "You should type undefined variable here"
    test_is_initial_text(path, text, error_text)
    test_window_text_deleted(path, text_with_deleted_windows, error_text)

    test_is_identifier(path)
