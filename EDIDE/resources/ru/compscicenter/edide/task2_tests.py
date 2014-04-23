import unittest
import sys
import os
import match_ends as me

class FirstTaskTestCase(unittest.TestCase):

    def test_task(self):
        self.assertEqual(me.match_ends(['aba', 'xyz', 'aa', 'x', 'bbb']), 3)
        self.assertEqual(me.match_ends(['', 'x', 'xy', 'xyx', 'xx']), 2)
        self.assertEqual(me.match_ends(['aaa', 'be', 'abc', 'hello']), 1)

if __name__ == '__main__':
    unittest.main()