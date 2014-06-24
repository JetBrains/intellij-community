from test_helper import test_is_empty, test_is_initial_text, test_window_text_deleted


if __name__ == '__main__':
    # TODO: get filepath. Let's now assume that we pass it as the last item in command-line
    import sys
    path = sys.argv[-1]
    test_is_empty(path)
    text = '''variable = 1
print (other variable)'''
    text_with_deleted_windows = '''variable = 1
print ()'''
    error_text = "You should type undefined variable here"
    test_is_initial_text(path, text, error_text)
    test_window_text_deleted(path, text_with_deleted_windows, error_text)
